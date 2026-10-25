package com.exem.dust.tcp;

import com.exem.dust.common.codec.DecodeResult;
import com.exem.dust.common.codec.NdjsonCodec;
import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Bye;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.common.protocol.Welcome;
import com.exem.dust.config.DustProperties;
import com.exem.dust.domain.station.RegisteredStation;
import com.exem.dust.service.AuthResult;
import com.exem.dust.service.IngestResult;
import com.exem.dust.service.IngestService;
import com.exem.dust.service.MessageValidator;
import com.exem.dust.service.StationAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final DustProperties.Tcp tcp;
    private final NdjsonCodec codec;
    private final StationAuthService authService;
    private final MessageValidator validator;
    private final IngestService ingestService;
    private final SessionRegistry sessions;

    private RegisteredStation station;
    private int lastSeenSeq;

    ConnectionHandler(Socket socket, DustProperties.Tcp tcp, NdjsonCodec codec, StationAuthService authService,
                      MessageValidator validator, IngestService ingestService, SessionRegistry sessions) {
        this.socket = socket;
        this.remoteAddress = socket.getRemoteSocketAddress().toString();
        this.tcp = tcp;
        this.codec = codec;
        this.authService = authService;
        this.validator = validator;
        this.ingestService = ingestService;
        this.sessions = sessions;
    }

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

    private boolean performHandshake(BoundedLineReader reader, MessageWriter writer) throws IOException {
        socket.setSoTimeout((int) tcp.helloTimeout().toMillis());
        LineRead firstLine;
        try {
            firstLine = reader.readLine();
        } catch (SocketTimeoutException e) {
            rejectAndLog(writer, RejectReason.HELLO_TIMEOUT, "접속 후 %d초 안에 HELLO가 필요".formatted(tcp.helloTimeout().toSeconds()), null);
            return false;
        }
        return switch (firstLine) {
            case LineRead.EndOfStream _ -> false;
            case LineRead.TooLong _ -> {
                rejectAndLog(writer, RejectReason.HELLO_EXPECTED, "메시지가 너무 깁니다", null);
                yield false;
            }
            case LineRead.Line(String text) -> switch (codec.decode(text)) {
                case DecodeResult.Invalid(var reason, var detail) -> {
                    rejectAndLog(writer, RejectReason.HELLO_EXPECTED, detail + " (" + reason + ")", text);
                    yield false;
                }
                case DecodeResult.Decoded(Hello hello) -> authenticate(hello, writer);
                case DecodeResult.Decoded(Message other) -> {
                    rejectAndLog(writer, RejectReason.HELLO_EXPECTED, "첫 메시지는 HELLO여야 합니다", text);
                    yield false;
                }
            };
        };
    }

    private boolean authenticate(Hello hello, MessageWriter writer) throws IOException {
        return switch (authService.authenticate(hello)) {
            case AuthResult.Rejected(var reason, var detail) -> {
                rejectAndLog(writer, reason, detail, hello.toString());
                yield false;
            }
            case AuthResult.Authenticated(var authenticated) -> {
                station = authenticated;
                var replaced = sessions.register(station.id(), this);
                if (replaced != null) {
                    log.info("[{}] 기존 접속을 새 접속으로 교체", describe());
                    replaced.closeQuietly();
                }
                int resumeFromSeq = ingestService.nextExpectedSeq(station.id());
                lastSeenSeq = resumeFromSeq - 1;
                socket.setSoTimeout((int) tcp.readTimeout().toMillis());
                writer.send(new Welcome(resumeFromSeq));
                log.info("[{}] 인증 완료. seq {}부터 수신 (측정소가 알린 lastAckedSeq={})", describe(), resumeFromSeq, hello.lastAckedSeq());
                yield true;
            }
        };
    }

    private void serve(BoundedLineReader reader, MessageWriter writer) throws IOException {
        while (true) {
            switch (reader.readLine()) {
                case LineRead.EndOfStream _ -> {
                    log.info("[{}] 측정소가 연결을 닫음", describe());
                    return;
                }
                case LineRead.TooLong _ -> writer.send(new Reject(RejectReason.MALFORMED, "메시지가 너무 깁니다"));
                case LineRead.Line(String text) -> {
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
            case DecodeResult.Invalid(var reason, var detail) -> {
                log.warn("[{}] 거부 {}: {}", describe(), reason, detail);
                writer.send(new Reject(reason, detail));
                yield true;
            }
            case DecodeResult.Decoded(Data data) -> handleData(data, writer);
            case DecodeResult.Decoded(Bye _) -> {
                log.info("[{}] BYE 수신, 정상 종료", describe());
                yield false;
            }
            case DecodeResult.Decoded(Message other) -> {
                writer.send(new Reject(RejectReason.UNEXPECTED_MESSAGE, "인증 후에는 DATA와 BYE만 받습니다"));
                yield true;
            }
        };
    }

    private boolean handleData(Data data, MessageWriter writer) throws IOException {
        var violation = validator.validate(data, station.id());
        if (violation.isPresent()) {
            var reject = violation.get();
            log.warn("[{}] seq={} 거부 {}: {}", describe(), data.seq(), reject.reason(), reject.detail());
            writer.send(reject);
            if (reject.reason().closesConnection()) {
                return false;
            }
            if (data.seq() > lastSeenSeq) {
                // 거부된 건의 순번은 소비한 것으로 본다. 측정소가 그 건을 건너뛰고 다음 건을 보낼 수 있게 한다
                lastSeenSeq = data.seq();
            }
            return true;
        }

        var result = ingestService.ingest(station, data);
        lastSeenSeq = Math.max(lastSeenSeq, data.seq());
        writer.send(new Ack(data.seq()));
        if (result instanceof IngestResult.Duplicate) {
            log.info("[{}] seq={} {} 중복 수신, 저장 생략 후 ACK 재전송", describe(), data.seq(), data.measuredAt());
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] seq={} {} 저장", describe(), data.seq(), data.measuredAt());
        }
        return true;
    }

    private void rejectAndLog(MessageWriter writer, RejectReason reason, String detail, String rawMessage) throws IOException {
        log.warn("[{}] 접속 거부 {}: {}", describe(), reason, detail);
        writer.send(new Reject(reason, detail));
    }

    private String describe() {
        return station == null ? remoteAddress : station.id() + " " + station.name() + " " + remoteAddress;
    }
}
