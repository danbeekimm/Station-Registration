package com.exem.dust.simulator.transport;

import com.exem.dust.common.data.DustRecord;
import com.exem.dust.common.data.StationData;
import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Bye;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Protocol;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.common.protocol.Welcome;
import com.exem.dust.simulator.ExitCode;
import com.exem.dust.simulator.cli.SimulatorOptions;
import com.exem.dust.simulator.cli.StationConsole;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 송신 루프. 접속 → HELLO/WELCOME → 고정 주기로 DATA 전송·ACK 확인 → BYE 순서로 진행하고,
 * 접속이 끊기면 백오프 후 재접속해 서버가 알려준 지점부터 이어서 보낸다.
 */
public final class TransmissionLoop {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");

    private final StationData station;
    private final List<DustRecord> records;
    private final String token;
    private final SimulatorOptions options;
    private final StationConsole console;
    private final ReconnectBackoff backoff = new ReconnectBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    /** 마지막으로 ACK 받은 seq. 재접속 HELLO에 실어 서버가 재개 지점을 계산하게 한다. */
    private int lastAckedSeq = 0;

    public TransmissionLoop(StationData station, String token, SimulatorOptions options, StationConsole console) {
        this.station = station;
        this.records = station.records();
        this.token = token;
        this.options = options;
        this.console = console;
    }

    /** 접속·전송·재접속을 반복한다. 전송을 끝냈거나 재시도가 무의미한 거부(미등록·토큰 불일치)를 받을 때만 끝난다. */
    public ExitCode run() {
        while (true) {
            try (var connection = ServerConnection.open(options.server(), CONNECT_TIMEOUT, options.ackTimeout())) {
                console.info("접속 " + options.server());
                var greeting = handshake(connection);
                switch (greeting) {
                    case Welcome welcome -> {
                        backoff.reset();
                        if (transmitFrom(connection, welcome.resumeFromSeq()) == TransmitOutcome.COMPLETED) {
                            return ExitCode.SUCCESS;
                        }
                        // RESYNC: 재개 지점을 다시 받기 위해 접속을 끊고 아래 백오프 뒤 재접속한다
                    }
                    case Reject reject -> {
                        return handleHelloReject(reject);
                    }
                    default -> throw new ProtocolException("HELLO 응답이 WELCOME/REJECT가 아닙니다: " + greeting);
                }
            } catch (IOException e) {
                console.warn("연결 문제: %s".formatted(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ExitCode.FAILURE;
            }
            if (!waitBeforeReconnect()) {
                return ExitCode.FAILURE;
            }
        }
    }

    /** HELLO에 마지막 ACK seq를 실어 보내고 첫 응답(WELCOME 또는 REJECT)을 돌려준다. */
    private Message handshake(ServerConnection connection) throws IOException {
        connection.send(new Hello(station.stationCode(), station.stationName(), token, Protocol.VERSION, lastAckedSeq));
        return connection.receive();
    }

    /** 인증 거부는 재시도해도 소용없다. 등록 명령을 안내하고 사유에 맞는 종료 코드를 정한다. */
    private ExitCode handleHelloReject(Reject reject) {
        if (reject.reason().isAuthFailure()) {
            console.error("서버가 측정소를 거부했습니다 (%s: %s). 중앙 서버에 측정소를 등록한 뒤 다시 실행하세요: ./gradlew registerStation --code=%s --name=%s --token=<토큰>"
                    .formatted(reject.reason(), reject.detail(), station.stationCode(), station.stationName()));
            return ExitCode.AUTH_REJECTED;
        }
        console.error("서버가 접속을 거부했습니다 (%s: %s)".formatted(reject.reason(), reject.detail()));
        return ExitCode.FAILURE;
    }

    /** 서버가 알려 준 seq부터 마지막 건까지 고정 주기로 보내고 BYE로 끝낸다. 재동기화가 필요하면 중간에 돌아온다. */
    private TransmitOutcome transmitFrom(ServerConnection connection, int resumeFromSeq) throws IOException, InterruptedException {
        int firstSeq = Math.max(1, resumeFromSeq);
        int lastSeq = records.size();
        if (firstSeq > lastSeq) {
            console.info("서버가 이미 %d건을 모두 보유. 보낼 데이터 없음".formatted(lastSeq));
            connection.send(new Bye(station.stationCode()));
            return TransmitOutcome.COMPLETED;
        }
        console.info("인증 완료. seq %d부터 %d건을 %dms 간격으로 전송".formatted(firstSeq, lastSeq - firstSeq + 1, options.interval().toMillis()));

        var schedule = new FixedRateSchedule(options.interval());
        for (int seq = firstSeq; seq <= lastSeq; seq++) {
            schedule.awaitSlot(seq - firstSeq);
            var data = toData(seq);
            switch (deliver(connection, data)) {
                case ACKED -> lastAckedSeq = seq;
                case SKIPPED -> { }
                case RESYNC -> {
                    return TransmitOutcome.RESYNC;
                }
            }
        }
        connection.send(new Bye(station.stationCode()));
        console.info("전송 완료 (%d건). BYE".formatted(lastSeq));
        return TransmitOutcome.COMPLETED;
    }

    /** DATA 1건을 보내고 ACK를 기다린다. ACK가 제때 오지 않으면 같은 건을 다시 보내고, 한도를 넘으면 재접속한다. */
    private DeliveryResult deliver(ServerConnection connection, Data data) throws IOException {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            connection.send(data);
            try {
                return awaitResponse(connection, data);
            } catch (SocketTimeoutException timeout) {
                console.warn("seq=%d ACK 없음 (%d/%d). 재전송".formatted(data.seq(), attempt, MAX_SEND_ATTEMPTS));
            }
        }
        throw new IOException("seq=%d ACK를 %d회 받지 못했습니다".formatted(data.seq(), MAX_SEND_ATTEMPTS));
    }

    /** 보낸 건의 ACK나 REJECT가 올 때까지 읽는다. 앞선 재전송의 늦은 ACK는 건너뛴다. */
    private DeliveryResult awaitResponse(ServerConnection connection, Data data) throws IOException {
        while (true) {
            var response = connection.receive();
            switch (response) {
                case Ack ack when ack.seq() == data.seq() -> {
                    console.info(describe(data) + " → ACK");
                    return DeliveryResult.ACKED;
                }
                case Ack staleAck when staleAck.seq() < data.seq() -> {
                    // 앞서 재전송한 건의 늦은 ACK. 기다리던 ACK가 아니므로 계속 읽는다
                }
                case Ack unexpectedAck -> throw new ProtocolException("보내지 않은 seq의 ACK: " + unexpectedAck.seq());
                case Reject reject -> {
                    return handleDataReject(data, reject);
                }
                default -> throw new ProtocolException("DATA 응답이 ACK/REJECT가 아닙니다: " + response);
            }
        }
    }

    /** 순서 문제나 치명적 사유면 재접속해 재개 지점을 다시 받고, 그 건만의 문제면 건너뛰어 전송을 이어간다. */
    private DeliveryResult handleDataReject(Data data, Reject reject) {
        if (reject.reason().closesConnection() || reject.reason() == RejectReason.OUT_OF_ORDER) {
            console.warn("%s → 거부 (%s: %s). 재접속해 재개 지점을 다시 받음".formatted(describe(data), reject.reason(), reject.detail()));
            return DeliveryResult.RESYNC;
        }
        console.warn("%s → 거부 (%s: %s). 이 건은 건너뜀".formatted(describe(data), reject.reason(), reject.detail()));
        return DeliveryResult.SKIPPED;
    }

    /** 백오프만큼 기다린다. 인터럽트되면 false를 돌려 루프를 끝낸다. */
    private boolean waitBeforeReconnect() {
        var delay = backoff.nextDelay();
        console.warn("%d초 후 재접속".formatted(delay.toSeconds()));
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** seq번째(1부터) 원본 행을 DATA 메시지로 바꾼다. 오존은 제공 데이터에 없어 항상 null. */
    private Data toData(int seq) {
        var record = records.get(seq - 1);
        return new Data(station.stationCode(), seq, record.measuredAt(), record.pm10(), record.pm25(), null);
    }

    /** 로그 한 줄용 요약: seq, 측정 시각(시 단위), 농도. */
    private static String describe(Data data) {
        return "seq=%d %s pm10=%s pm25=%s".formatted(data.seq(), HOUR_FORMAT.format(data.measuredAt()), data.pm10(), data.pm25());
    }

    private enum TransmitOutcome { COMPLETED, RESYNC }

    private enum DeliveryResult { ACKED, SKIPPED, RESYNC }
}
