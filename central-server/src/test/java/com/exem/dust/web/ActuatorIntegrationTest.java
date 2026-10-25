package com.exem.dust.web;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.support.PostgresTestContainerConfiguration;
import com.exem.dust.support.TestStationClient;
import com.exem.dust.tcp.TcpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static com.exem.dust.support.TestStationClient.NOWON_ID;
import static com.exem.dust.support.TestStationClient.NOWON_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

/** health와 prometheus 엔드포인트만 열려 있고, 수신·거부가 dust_* 지표로 집계되는지 본다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dust.tcp.port=0", "dust.slack.auto-dispatch=false"})
@Import(PostgresTestContainerConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ActuatorIntegrationTest {

    @LocalServerPort
    int httpPort;
    @Autowired
    TcpServer tcpServer;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void healthIsUpAndOnlyAllowedEndpointsAreExposed() throws Exception {
        assertThat(get("/actuator/health").body()).contains("\"status\":\"UP\"");
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
    }

    @Test
    void prometheusExposesDomainCounters() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 30, 0, 0), 10.0, 5.0, null));
            client.request(new Data("111121", 2, LocalDateTime.of(2023, 3, 30, 1, 0), 10.0, 5.0, null)); // FORBIDDEN
        }

        var body = get("/actuator/prometheus").body();

        assertThat(body).contains("dust_messages_received_total{station=\"111311\"}");
        assertThat(body).contains("dust_messages_rejected_total{reason=\"FORBIDDEN\"}");
        assertThat(body).contains("dust_connections_active");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
