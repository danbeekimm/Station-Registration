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

    public DustMetrics(MeterRegistry registry, SessionRegistry sessions) {
        this.registry = registry;
        Gauge.builder("dust.connections.active", sessions, SessionRegistry::activeCount)
                .description("인증된 측정소 접속 수")
                .register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurement(MeasurementStoredEvent event) {
        registry.counter("dust.messages.received", "station", event.measurement().stationId()).increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlert(AlertIssuedEvent event) {
        var alert = event.alert();
        registry.counter("dust.alerts.issued", "station", alert.stationId(), "grade", String.valueOf(alert.rule().grade())).increment();
    }

    @EventListener
    public void onReject(ConnectionRejectedEvent event) {
        registry.counter("dust.messages.rejected", "reason", event.reason().name()).increment();
    }

    @EventListener
    public void onSlackDelivery(SlackDeliveryEvent event) {
        registry.counter("dust.slack.deliveries", "status", event.status()).increment();
    }
}
