package com.exem.dust.web.sse;

import com.exem.dust.domain.event.AlertIssuedEvent;
import com.exem.dust.domain.event.MaintenanceChangedEvent;
import com.exem.dust.domain.event.MeasurementStoredEvent;
import com.exem.dust.domain.maintenance.MaintenanceChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 브라우저로 Server-Sent Events를 밀어 준다. 수신 트랜잭션이 커밋된 뒤의 도메인 이벤트만 구독하므로
 * 화면에 보이는 값은 항상 DB에 있는 값이다. 끊긴 브라우저는 EventSource가 스스로 재접속한다.
 */
@Component
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);
    private static final long NO_TIMEOUT = 0L;

    /** 화면이 쓰는 이벤트 payload. */
    public record MeasurementEvent(String stationId, LocalDateTime at, Double pm10, Double pm25) {
    }

    /** 경보 1건. 피드 행과 카드 배지를 갱신한다. */
    public record AlertEvent(String stationId, LocalDateTime issuedAt, int grade, String level, String pollutant,
                             double triggerValue, int sustainedHours) {
    }

    /** 점검 변화 1건. kind는 started·extended·ended. */
    public record MaintenanceEvent(String stationId, String equipment, LocalDateTime startedAt, LocalDateTime endedAt,
                                   int hours, String kind) {
    }

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();

    /** 새 구독자. 완료·타임아웃·오류 어느 쪽으로 끝나도 목록에서 스스로 빠진다. */
    public SseEmitter subscribe() {
        var emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        return emitter;
    }

    /** 현재 열린 브라우저 수. */
    public int subscriberCount() {
        return emitters.size();
    }

    /** 저장이 커밋된 측정값. 카드의 최신 농도와 차트 점이 된다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurement(MeasurementStoredEvent event) {
        var measurement = event.measurement();
        broadcast("measurement", new MeasurementEvent(measurement.stationId(), measurement.measuredAt(), measurement.pm10(), measurement.pm25()));
    }

    /** 기록이 커밋된 경보. 룰 정보를 화면용 필드로 풀어 보낸다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlert(AlertIssuedEvent event) {
        var alert = event.alert();
        broadcast("alert", new AlertEvent(alert.stationId(), alert.issuedAt(), alert.rule().grade(), alert.rule().label(),
                alert.rule().pollutant().label(), alert.triggerValue(), alert.sustainedHours()));
    }

    /** 기록이 커밋된 점검 변화. 종류를 문자열로 함께 보내 화면이 태그를 붙이거나 뗀다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaintenance(MaintenanceChangedEvent event) {
        var window = event.change().window();
        var kind = switch (event.change()) {
            case MaintenanceChange.Started _ -> "started";
            case MaintenanceChange.Extended _ -> "extended";
            case MaintenanceChange.Ended _ -> "ended";
        };
        broadcast("maintenance", new MaintenanceEvent(window.stationId(), window.equipment().name(), window.startedAt(),
                window.endedAt(), window.hours(), kind));
    }

    /** 모든 구독자에게 보낸다. 전송에 실패한 구독자는 떠난 것으로 보고 제거한다. */
    private void broadcast(String eventName, Object payload) {
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // 브라우저가 떠난 접속. 목록에서 빼고 나머지에는 계속 보낸다
                emitters.remove(emitter);
                log.debug("SSE 구독 종료: {}", e.getMessage());
            }
        }
    }
}
