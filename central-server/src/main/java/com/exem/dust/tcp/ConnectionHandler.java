package com.exem.dust.tcp;

import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.common.codec.dto.DecodedMessage;
import com.exem.dust.common.codec.dto.InvalidMessage;
import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Bye;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.common.protocol.Welcome;
import com.exem.dust.config.DustProperties;
import com.exem.dust.config.dto.TcpProperties;
import com.exem.dust.domain.reject.RawMessageMasker;
import com.exem.dust.domain.station.RegisteredStation;
import com.exem.dust.domain.station.Station;
import com.exem.dust.service.IngestService;
import com.exem.dust.service.MessageValidator;
import com.exem.dust.service.RejectRecorder;
import com.exem.dust.service.StationAuthService;
import com.exem.dust.service.StationStateLoader;
import com.exem.dust.service.dto.AuthRejected;
import com.exem.dust.service.dto.IngestResult;
import com.exem.dust.service.dto.MeasurementDuplicate;
import com.exem.dust.service.dto.StationAuthenticated;
import com.exem.dust.service.dto.ValidationContext;
import com.exem.dust.tcp.dto.EndOfStream;
import com.exem.dust.tcp.dto.LineRead;
import com.exem.dust.tcp.dto.LineTooLong;
import com.exem.dust.tcp.dto.ReadLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 접속 하나의 수명을 담당한다. 상태는 두 단계다.
 * <ol>
 *   <li>인증 전: 첫 줄은 HELLO여야 하고 제한 시간 안에 와야 한다. 그 외는 REJECT 후 종료.</li>
 *   <li>인증 후: DATA는 검증 → 저장 → ACK, BYE는 정상 종료, 나머지는 REJECT(접속 유지).</li>
 * </ol>
 * 가상 스레드 하나가 접속 하나를 끝까지 처리하므로 이 객체의 상태는 그 스레드만 바꾼다.
 */
final class ConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private final Socket socket;
    private final String remoteAddress;
    private final String remoteIp;
    private final TcpProperties tcp;
    private final int maxConsecutiveOutOfOrder;
    private final NdjsonCodec codec;
    private final StationAuthService authService;
    private final StationStateLoader stateLoader;
    private final MessageValidator validator;
    private final IngestService ingestService;
    private final RejectRecorder rejectRecorder;
    private final SessionRegistry sessions;

    private RegisteredStation station;
    /** 인증 후 DB에서 복원한 판정 상태. 이 접속의 스레드만 갱신한다. */
    private Station state;
    private int lastSeenSeq;
    private int consecutiveOutOfOrder;

    ConnectionHandler(Socket socket, DustProperties properties, NdjsonCodec codec, StationAuthService authService,
                      StationStateLoader stateLoader, MessageValidator validator, IngestService ingestService,
                      RejectRecorder rejectRecorder, SessionRegistry sessions) {
        this.socket = socket;
        this.remoteAddress = socket.getRemoteSocketAddress().toString();
        this.remoteIp = socket.getInetAddress().getHostAddress();
        this.tcp = properties.tcp();
        this.maxConsecutiveOutOfOrder = properties.validation().maxConsecutiveOutOfOrder();
        this.codec = codec;
        this.authService = authService;
        this.stateLoader = stateLoader;
        this.validator = validator;
        this.ingestService = ingestService;
        this.rejectRecorder = rejectRecorder;
        this.sessions = sessions;
    }

    /** 접속 하나의 전체 수명. 어떤 경로로 끝나든 소켓을 닫고 세션 등록을 푼다. */
    @Override
    public void run() {
        try (socket;
             var reader = new BoundedLineReader(socket.getInputStream(), tcp.maxLineBytes());
             var writer = new MessageWriter(socket.getOutputStream(), codec)) {
            if (performHandshake(reader, writer)) {
                serve(reader, writer);
            }
        } catch (SocketTimeoutException e) {
            log.warn("[{}] 읽기 시간 초과로 연결 종료", describe());
        } catch (IOException e) {
            log.info("[{}] 연결 종료: {}", describe(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("[{}] 처리 중 오류로 연결 종료", describe(), e);
        } finally {
            if (station != null) {
                sessions.unregister(station.id(), this);
            }
        }
    }

    /** 다른 스레드(세션 교체, 서버 종료)에서 호출된다. 읽기 중인 스레드는 SocketException으로 깨어나 정리한다. */
    void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫힌 소켓
        }
    }

    /** 첫 줄이 제한 시간 안에 온 HELLO인지 확인하고 인증으로 넘긴다. @return 인증까지 성공하면 true */
    private boolean performHandshake(BoundedLineReader reader, MessageWriter writer) throws IOException {
        socket.setSoTimeout((int) tcp.helloTimeout().toMillis());
        LineRead firstLine;
        try {
            firstLine = reader.readLine();
        } catch (SocketTimeoutException e) {
            rejectAndLog(writer, RejectReason.HELLO_TIMEOUT, "접속 후 %d초 안에 HELLO가 필요".formatted(tcp.helloTimeout().toSeconds()), null, null);
            return false;
        }
        return switch (firstLine) {
            case EndOfStream _ -> false;
            case LineTooLong _ -> {
                rejectAndLog(writer, RejectReason.HELLO_EXPECTED, "메시지가 너무 깁니다", null, null);
                yield false;
            }
            case ReadLine(String text) -> switch (codec.decode(text)) {
                case InvalidMessage(var reason, var detail) -> {
                    rejectAndLog(writer, RejectReason.HELLO_EXPECTED, detail + " (" + reason + ")", null, text);
                    yield false;
                }
                case DecodedMessage(Hello hello) -> authenticate(hello, writer);
                case DecodedMessage(Message other) -> {
                    rejectAndLog(writer, RejectReason.HELLO_EXPECTED, "첫 메시지는 HELLO여야 합니다", null, text);
                    yield false;
                }
            };
        };
    }

    /** 토큰 검증 → 세션 등록(기존 접속 교체) → 상태 복원 → WELCOME 순. 이후부터 읽기 타임아웃이 30초로 바뀐다. */
    private boolean authenticate(Hello hello, MessageWriter writer) throws IOException {
        return switch (authService.authenticate(hello)) {
            case AuthRejected(var reason, var detail) -> {
                // Hello.toString()은 토큰을 가린 표현이라 원문 대신 저장해도 안전하다
                rejectAndLog(writer, reason, detail, hello.stationId(), hello.toString());
                yield false;
            }
            case StationAuthenticated(var authenticated) -> {
                station = authenticated;
                var replaced = sessions.register(station.id(), this);
                if (replaced != null) {
                    log.info("[{}] 기존 접속을 새 접속으로 교체", describe());
                    replaced.closeQuietly();
                }
                state = stateLoader.load(station.id());
                int resumeFromSeq = ingestService.nextExpectedSeq(station.id());
                lastSeenSeq = resumeFromSeq - 1;
                socket.setSoTimeout((int) tcp.readTimeout().toMillis());
                writer.send(new Welcome(resumeFromSeq));
                log.info("[{}] 인증 완료. seq {}부터 수신 (측정소가 알린 lastAckedSeq={})", describe(), resumeFromSeq, hello.lastAckedSeq());
                yield true;
            }
        };
    }

    /** 인증 후 메시지 루프. BYE, 치명적 거부, 저장 실패, 상대의 연결 종료 중 하나로 끝난다. */
    private void serve(BoundedLineReader reader, MessageWriter writer) throws IOException {
        while (true) {
            switch (reader.readLine()) {
                case EndOfStream _ -> {
                    log.info("[{}] 측정소가 연결을 닫음", describe());
                    return;
                }
                    case LineTooLong _ -> rejectAndLog(writer, RejectReason.MALFORMED, "메시지가 너무 깁니다", station.id(), null);
                case ReadLine(String text) -> {
                    if (!handle(text, writer)) {
                        return;
                    }
                }
            }
        }
    }

    /** @return 접속을 계속 유지하면 true */
    private boolean handle(String text, MessageWriter writer) throws IOException {
        return switch (codec.decode(text)) {
            case InvalidMessage(var reason, var detail) -> {
                rejectAndLog(writer, reason, detail, station.id(), text);
                yield true;
            }
            case DecodedMessage(Data data) -> handleData(data, writer, text);
            case DecodedMessage(Bye _) -> {
                log.info("[{}] BYE 수신, 정상 종료", describe());
                yield false;
            }
            case DecodedMessage(Message other) -> {
                rejectAndLog(writer, RejectReason.UNEXPECTED_MESSAGE, "인증 후에는 DATA와 BYE만 받습니다", station.id(), text);
                yield true;
            }
        };
    }

    /** 검증 → 저장·판정 → ACK. 거부된 건의 seq는 소비해 측정소가 다음 건을 이어 보낼 수 있게 한다. @return 접속 유지 여부 */
    private boolean handleData(Data data, MessageWriter writer, String rawText) throws IOException {
        var context = new ValidationContext(station.id(), lastSeenSeq, state.lastMeasuredAt());
        var violation = validator.validate(data, context);
        if (violation.isPresent()) {
            var reject = violation.get();
            rejectAndLog(writer, reject.reason(), "seq=%d %s".formatted(data.seq(), reject.detail()), station.id(), rawText);
            if (reject.reason().closesConnection()) {
                return false;
            }
            if (reject.reason() == RejectReason.OUT_OF_ORDER) {
                // 순서가 계속 어긋나면 측정소가 재접속해 재개 지점을 다시 받도록 접속을 끊는다
                consecutiveOutOfOrder++;
                return consecutiveOutOfOrder < maxConsecutiveOutOfOrder;
            }
            // 거부된 건의 순번은 소비한 것으로 본다. 측정소가 그 건을 건너뛰고 다음 건을 보낼 수 있게 한다
            lastSeenSeq = Math.max(lastSeenSeq, data.seq());
            return true;
        }
        consecutiveOutOfOrder = 0;

        IngestResult result;
        try {
            result = ingestService.ingest(state, data);
        } catch (DataAccessException e) {
            // 메모리 상태는 바뀌었을 수 있는데 DB는 아니다. 접속을 끊어 재접속 시 DB에서 상태를 다시 복원하게 한다
            log.error("[{}] seq={} 저장 실패, 접속 종료: {}", describe(), data.seq(), e.getMostSpecificCause().getMessage());
            return false;
        }
        lastSeenSeq = Math.max(lastSeenSeq, data.seq());
        writer.send(new Ack(data.seq()));
        if (result instanceof MeasurementDuplicate) {
            log.info("[{}] seq={} {} 중복 수신, 저장 생략 후 ACK 재전송", describe(), data.seq(), data.measuredAt());
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] seq={} {} 저장", describe(), data.seq(), data.measuredAt());
        }
        return true;
    }

    /** 거부 응답을 보내고 서버 로그와 connection_reject_log에 남긴다. 원문의 토큰 값은 가려서 저장한다. */
    private void rejectAndLog(MessageWriter writer, RejectReason reason, String detail, String claimedStationId,
                              String rawMessage) throws IOException {
        log.warn("[{}] 거부 {}: {}", describe(), reason, detail);
        writer.send(new Reject(reason, detail));
        rejectRecorder.record(remoteIp, claimedStationId, reason, RawMessageMasker.maskToken(rawMessage));
    }

    /** 로그용 접속 식별자. 인증 후에는 측정소 코드·이름이 붙는다. */
    private String describe() {
        return station == null ? remoteAddress : station.id() + " " + station.name() + " " + remoteAddress;
    }
}
