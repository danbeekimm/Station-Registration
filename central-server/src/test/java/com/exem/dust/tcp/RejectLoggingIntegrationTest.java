package com.exem.dust.tcp;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Protocol;
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

/** 거부가 connection_reject_log에 남는지, 저장된 원문에 토큰이 노출되지 않는지 본다. */
class RejectLoggingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    TcpServer tcpServer;

    @Test
    void authFailuresAreRecordedWithMaskedToken() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, "secret-but-wrong");
        }
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello("111191", "dev-token-eunpyeong-111191");
        }
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            // JSON이 깨진 HELLO: 원문이 그대로 저장되면 토큰이 노출되므로 가려져야 한다
            client.sendRaw("{\"type\":\"HELLO\",\"stationId\":\"111311\",\"token\":\"secret-in-broken-json\",");
            client.receive();
        }

        List<Map<String, Object>> rows = jdbc.sql(
                        "SELECT station_id, reason_code, raw_message, remote_ip FROM connection_reject_log ORDER BY id")
                .query().listOfRows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("station_id", NOWON_ID).containsEntry("reason_code", "AUTH_FAILED").containsEntry("remote_ip", "127.0.0.1");
        assertThat(rows.get(1)).containsEntry("station_id", "111191").containsEntry("reason_code", "UNKNOWN_STATION");
        assertThat(rows.get(2)).containsEntry("reason_code", "HELLO_EXPECTED");
        assertThat(rows).extracting(row -> String.valueOf(row.get("raw_message")))
                .noneMatch(raw -> raw.contains("secret"));
        assertThat(String.valueOf(rows.get(2).get("raw_message"))).contains("\"token\":\"****\"");
    }

    @Test
    void validationFailuresAfterAuthAreRecordedWithStationId() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.sendRaw("not json at all");
            client.receive();
            client.request(new Hello(NOWON_ID, "노원구", NOWON_TOKEN, Protocol.VERSION, 0));
            client.request(new Data("111121", 1, LocalDateTime.of(2023, 3, 1, 0, 0), 1.0, 1.0, null));
        }

        List<String> reasons = jdbc.sql("SELECT reason_code FROM connection_reject_log ORDER BY id").query(String.class).list();
        assertThat(reasons).containsExactly("MALFORMED", "UNEXPECTED_MESSAGE", "FORBIDDEN");
        assertThat(jdbc.sql("SELECT DISTINCT station_id FROM connection_reject_log").query(String.class).list())
                .containsExactly(NOWON_ID);
    }

    @Test
    void acceptedTrafficLeavesNoRejectRows() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 1.0, 1.0, null));
        }

        assertThat(jdbc.sql("SELECT count(*) FROM connection_reject_log").query(Long.class).single()).isZero();
    }
}
