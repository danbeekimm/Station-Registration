package com.exem.dust.metrics;

import com.exem.dust.domain.event.AlertIssuedEvent;
import com.exem.dust.domain.event.ConnectionRejectedEvent;
import com.exem.dust.domain.event.MeasurementStoredEvent;
import com.exem.dust.domain.event.SlackDeliveryEvent;
import com.exem.dust.tcp.SessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트를 Micrometer 지표로 바꾼다. 서비스 계층은 지표 라이브러리를 모른다.
 * GET /actuator/prometheus 에서 dust_* 로 노출된다.
 */
@Component
public class DustMetrics {

    private final MeterRegistry registry;

    /** 접속 수는 이벤트 없이 레지스트리를 직접 읽는 게이지로 둔다. */
    public DustMetrics(MeterRegistry registry, SessionRegistry sessions) {
        this.registry = registry;
        Gauge.builder("dust.connections.active", sessions, SessionRegistry::activeCount)
                .description("인증된 측정소 접속 수")
                .register(registry);
    }

    /** 저장까지 끝난 DATA 수. 거부된 건은 세지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurement(MeasurementStoredEvent event) {
        registry.counter("dust.messages.received", "station", event.measurement().stationId()).increment();
    }

    /** 경보 발령 수. 측정소와 등급 태그로 나뉜다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlert(AlertIssuedEvent event) {
        var alert = event.alert();
        registry.counter("dust.alerts.issued", "station", alert.stationId(), "grade", String.valueOf(alert.rule().grade())).increment();
    }

    /** 인증·검증 거부 수. 트랜잭션 밖에서 발행되므로 일반 리스너다. */
    @EventListener
    public void onReject(ConnectionRejectedEvent event) {
        registry.counter("dust.messages.rejected", "reason", event.reason().name()).increment();
    }

    /** Slack 전송 시도 결과(SENT, RETRY, RATE_LIMITED, FAILED). */
    @EventListener
    public void onSlackDelivery(SlackDeliveryEvent event) {
        registry.counter("dust.slack.deliveries", "status", event.status()).increment();
    }
}
