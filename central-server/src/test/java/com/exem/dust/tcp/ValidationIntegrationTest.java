package com.exem.dust.tcp;

import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.support.IntegrationTestSupport;
import com.exem.dust.support.TestStationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.exem.dust.support.TestStationClient.NOWON_ID;
import static com.exem.dust.support.TestStationClient.NOWON_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

/** 거부된 DATA는 저장·판정되지 않고, 접속 유지 여부가 사유대로 결정되는지 본다. */
class ValidationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 1, 0, 0);

    @Autowired
    TcpServer tcpServer;

    @Test
    void skippedSeqIsRejectedAndConnectionSurvivesUntilThirdTime() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            assertThat(client.request(data(1, T0, 10.0, 5.0))).isEqualTo(new Ack(1));

            assertThat(reasonOf(client.request(data(3, T0.plusHours(2), 10.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_ORDER);
            assertThat(reasonOf(client.request(data(3, T0.plusHours(2), 10.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_ORDER);
            assertThat(client.request(data(2, T0.plusHours(1), 10.0, 5.0))).isEqualTo(new Ack(2)); // 순서를 바로잡으면 카운터 리셋

            assertThat(reasonOf(client.request(data(9, T0.plusHours(8), 10.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_ORDER);
            assertThat(reasonOf(client.request(data(9, T0.plusHours(8), 10.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_ORDER);
            assertThat(reasonOf(client.request(data(9, T0.plusHours(8), 10.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_ORDER);
            assertThat(client.isClosedByServer()).isTrue();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM measurement").query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void outOfRangeValueIsNotStoredAndNextSeqContinues() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0, 400.0, 5.0));
            assertThat(reasonOf(client.request(data(2, T0.plusHours(1), 5000.0, 5.0)))).isEqualTo(RejectReason.OUT_OF_RANGE);
            assertThat(client.request(data(3, T0.plusHours(2), 400.0, 5.0))).isEqualTo(new Ack(3));
        }

        assertThat(jdbc.sql("SELECT seq FROM measurement ORDER BY seq").query(Integer.class).list()).containsExactly(1, 3);
        // 01시가 거부되어 00시→02시는 시각 누락이므로 지속이 끊겨 경보도 없다
        assertThat(jdbc.sql("SELECT count(*) FROM alert_log").query(Long.class).single()).isZero();
    }

    @Test
    void timeGoingBackwardsOrOffTheHourIsRejected() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(data(1, T0.plusHours(5), 10.0, 5.0));
            assertThat(reasonOf(client.request(data(2, T0.plusHours(4), 10.0, 5.0)))).isEqualTo(RejectReason.BAD_TIME);
            assertThat(reasonOf(client.request(data(3, T0.plusHours(6).plusMinutes(30), 10.0, 5.0)))).isEqualTo(RejectReason.BAD_TIME);
            assertThat(client.request(data(4, T0.plusHours(6), 10.0, 5.0))).isEqualTo(new Ack(4));
        }

        assertThat(jdbc.sql("SELECT count(*) FROM connection_reject_log WHERE reason_code = 'BAD_TIME'").query(Long.class).single())
                .isEqualTo(2L);
    }

    private static RejectReason reasonOf(Object response) {
        assertThat(response).isInstanceOf(Reject.class);
        return ((Reject) response).reason();
    }

    private static Data data(int seq, LocalDateTime measuredAt, Double pm10, Double pm25) {
        return new Data(NOWON_ID, seq, measuredAt, pm10, pm25, null);
    }
}
