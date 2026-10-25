package com.exem.dust.tcp;

import com.exem.dust.common.protocol.Ack;
import com.exem.dust.common.protocol.Bye;
import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.common.protocol.Welcome;
import com.exem.dust.support.IntegrationTestSupport;
import com.exem.dust.support.TestStationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.exem.dust.support.TestStationClient.NOWON_ID;
import static com.exem.dust.support.TestStationClient.NOWON_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

class TcpIngestIntegrationTest extends IntegrationTestSupport {

    @Autowired
    TcpServer tcpServer;

    @Test
    void storesDataAfterHandshakeAndAcksDuplicatesWithoutStoringTwice() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            assertThat(client.hello(NOWON_ID, NOWON_TOKEN)).isEqualTo(new Welcome(1));

            var data = new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null);
            assertThat(client.request(data)).isEqualTo(new Ack(1));
            assertThat(client.request(data)).isEqualTo(new Ack(1));
            assertThat(client.request(new Data(NOWON_ID, 2, LocalDateTime.of(2023, 3, 1, 1, 0), null, 12.0, null)))
                    .isEqualTo(new Ack(2));

            client.send(new Bye(NOWON_ID));
            assertThat(client.isClosedByServer()).isTrue();
        }

        assertThat(jdbc.sql("SELECT count(*) FROM measurement").query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("SELECT pm10 FROM measurement WHERE seq = 2").query(Double.class).optional()).isEmpty();
    }

    @Test
    void reconnectResumesAfterLastStoredSeq() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null));
            client.request(new Data(NOWON_ID, 2, LocalDateTime.of(2023, 3, 1, 1, 0), 24.0, 12.0, null));
        }
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            assertThat(client.hello(NOWON_ID, NOWON_TOKEN)).isEqualTo(new Welcome(3));
        }
    }

    @Test
    void rejectsWrongTokenAndClosesConnection() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            var response = client.hello(NOWON_ID, "wrong-token");

            assertThat(response).isEqualTo(new Reject(RejectReason.AUTH_FAILED, "토큰 불일치"));
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void rejectsUnknownStation() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            assertThat(client.hello("111191", "dev-token-eunpyeong-111191"))
                    .isInstanceOfSatisfying(Reject.class, reject -> assertThat(reject.reason()).isEqualTo(RejectReason.UNKNOWN_STATION));
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void rejectsDataBeforeHello() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            var response = client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null));

            assertThat(response).isInstanceOfSatisfying(Reject.class, reject -> assertThat(reject.reason()).isEqualTo(RejectReason.HELLO_EXPECTED));
            assertThat(client.isClosedByServer()).isTrue();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM measurement").query(Long.class).single()).isZero();
    }

    @Test
    void rejectsDataForAnotherStationAndCloses() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            var response = client.request(new Data("111121", 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null));

            assertThat(response).isInstanceOfSatisfying(Reject.class, reject -> assertThat(reject.reason()).isEqualTo(RejectReason.FORBIDDEN));
            assertThat(client.isClosedByServer()).isTrue();
        }
    }

    @Test
    void malformedLineIsRejectedButConnectionStaysOpen() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.sendRaw("{not json");
            assertThat(client.receive()).isInstanceOfSatisfying(Reject.class, reject -> assertThat(reject.reason()).isEqualTo(RejectReason.MALFORMED));

            client.sendRaw("x".repeat(5000));
            assertThat(client.receive()).isInstanceOfSatisfying(Reject.class, reject -> assertThat(reject.reason()).isEqualTo(RejectReason.MALFORMED));

            assertThat(client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null)))
                    .isEqualTo(new Ack(1));
        }
    }

    @Test
    void newConnectionForSameStationReplacesOldOne() throws Exception {
        try (var first = new TestStationClient(tcpServer.boundPort());
             var second = new TestStationClient(tcpServer.boundPort())) {
            first.hello(NOWON_ID, NOWON_TOKEN);
            second.hello(NOWON_ID, NOWON_TOKEN);

            assertThat(first.isClosedByServer()).isTrue();
            assertThat(second.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null)))
                    .isEqualTo(new Ack(1));
        }
    }
}
