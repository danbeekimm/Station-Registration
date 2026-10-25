package com.exem.dust.tcp;

import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.support.IntegrationTestSupport;
import com.exem.dust.support.TestStationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.exem.dust.support.TestStationClient.NOWON_ID;
import static com.exem.dust.support.TestStationClient.NOWON_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

/** 수신 → 판정 → alert_log 기록이 실제 소켓과 DB를 거쳐 동작하는지, 재전송·재접속에도 결과가 같은지 본다. */
class AlertRecordingIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 23, 20, 0);

    @Autowired
    TcpServer tcpServer;

    @Test
    void recordsHighestGradeOncePerHourAndIgnoresResend() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, 230.0, 60.0));
            client.request(data(2, T0.plusHours(1), 307.0, 70.0));   // PM10 ≥150 2시간째 → 4등급
            client.request(data(3, T0.plusHours(2), 313.0, 80.0));   // PM10 ≥300 2시간째 → 2등급이 4등급보다 우선
            assertThat(client.request(data(3, T0.plusHours(2), 313.0, 80.0))).isEqualTo(new Ack(3)); // 재전송
        }

        List<Map<String, Object>> alerts = jdbc.sql("SELECT issued_at, grade, sustained_hours FROM alert_log ORDER BY issued_at")
                .query().listOfRows();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0)).containsEntry("grade", 4).containsEntry("sustained_hours", 2);
        assertThat(alerts.get(1)).containsEntry("grade", 2).containsEntry("sustained_hours", 2);
    }

    @Test
    void reconnectRestoresStreakFromStoredMeasurements() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, 160.0, 20.0));
            client.request(data(2, T0.plusHours(1), 160.0, 20.0));
        }
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(3, T0.plusHours(2), 160.0, 20.0));
        }

        List<Integer> sustained = jdbc.sql("SELECT sustained_hours FROM alert_log ORDER BY issued_at").query(Integer.class).list();
        assertThat(sustained).containsExactly(2, 3);
    }

    @Test
    void nullMeasurementBreaksStreakAcrossSession() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, 200.0, 20.0));
            client.request(data(2, T0.plusHours(1), null, 20.0));
            client.request(data(3, T0.plusHours(2), 200.0, 20.0));
        }

        assertThat(jdbc.sql("SELECT count(*) FROM alert_log").query(Long.class).single()).isZero();
    }

    private static Data data(int seq, LocalDateTime measuredAt, Double pm10, Double pm25) {
        return new Data(NOWON_ID, seq, measuredAt, pm10, pm25, null);
    }
}
