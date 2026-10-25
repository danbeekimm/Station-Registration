package com.exem.dust.tcp;

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

/** null 측정값이 장비별 점검 구간으로 병합 저장되는지, 재접속을 거쳐도 같은 행에 이어지는지 본다. */
class MaintenanceRecordingIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 8, 11, 0);

    @Autowired
    TcpServer tcpServer;

    @Test
    void consecutiveNullsBecomeOneWindowPerEquipment() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, 30.0, 15.0));
            client.request(data(2, T0.plusHours(1), null, 15.0));
            client.request(data(3, T0.plusHours(2), null, null));
            client.request(data(4, T0.plusHours(3), 28.0, 14.0));
        }

        List<Map<String, Object>> rows = jdbc.sql(
                        "SELECT equipment, started_at, ended_at, hours FROM maintenance_log ORDER BY equipment")
                .query().listOfRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("equipment", "PM10").containsEntry("hours", 2);
        assertThat(rows.get(0).get("started_at").toString()).startsWith("2023-03-08 12:00");
        assertThat(rows.get(0).get("ended_at").toString()).startsWith("2023-03-08 14:00");
        assertThat(rows.get(1)).containsEntry("equipment", "PM25").containsEntry("hours", 1);
    }

    @Test
    void reconnectDuringMaintenanceExtendsExistingRowInsteadOfStartingNew() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, null, 15.0));
            client.request(data(2, T0.plusHours(1), null, 15.0));
        }
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(3, T0.plusHours(2), null, 15.0));
        }

        List<Map<String, Object>> rows = jdbc.sql("SELECT hours, ended_at FROM maintenance_log").query().listOfRows();
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("hours", 3);
            assertThat(row.get("ended_at")).isNull();
        });
    }

    private static Data data(int seq, LocalDateTime measuredAt, Double pm10, Double pm25) {
        return new Data(NOWON_ID, seq, measuredAt, pm10, pm25, null);
    }
}
