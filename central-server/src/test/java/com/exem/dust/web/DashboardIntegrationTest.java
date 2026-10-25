package com.exem.dust.web;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.support.PostgresTestContainerConfiguration;
import com.exem.dust.support.TestStationClient;
import com.exem.dust.tcp.TcpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.exem.dust.support.TestStationClient.NOWON_ID;
import static com.exem.dust.support.TestStationClient.NOWON_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 HTTP 포트로 대시보드 페이지와 SSE 스트림을 확인한다. 수신 1건이 커밋되면 브라우저가 받을 이벤트가 흘러야 한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dust.tcp.port=0", "dust.slack.auto-dispatch=false"})
@Import(PostgresTestContainerConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class DashboardIntegrationTest {

    @LocalServerPort
    int httpPort;
    @Autowired
    TcpServer tcpServer;
    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM alert_log").update();
        jdbc.sql("DELETE FROM maintenance_log").update();
        jdbc.sql("DELETE FROM measurement").update();
    }

    @Test
    void dashboardRendersEveryRegisteredStationWithInitialData() throws Exception {
        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 23, 21, 0), 307.0, 47.0, null));
            client.request(new Data(NOWON_ID, 2, LocalDateTime.of(2023, 3, 23, 22, 0), 313.0, 44.0, null));
        }

        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("노원구").contains("중구").contains("강서구")
                .contains("미세먼지 경보 2등급")
                .contains("\"pm10\":313.0")
                .contains("new EventSource('/stream')");
    }

    @Test
    void streamPushesMeasurementAndAlertEventsAfterCommit() throws Exception {
        var received = new ArrayList<String>();
        var gotAlert = new CompletableFuture<Void>();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/stream"))
                .header("Accept", "text/event-stream").build();
        var streaming = http.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> response.body().forEach(line -> {
                    synchronized (received) {
                        received.add(line);
                    }
                    if (line.startsWith("event:alert")) {
                        gotAlert.complete(null);
                    }
                }));
        Thread.sleep(Duration.ofMillis(300)); // 구독이 등록될 시간

        try (var client = new TestStationClient(tcpServer.boundPort())) {
            client.hello(NOWON_ID, NOWON_TOKEN);
            client.request(new Data(NOWON_ID, 1, LocalDateTime.of(2023, 3, 19, 21, 0), 60.0, 95.0, null));
            client.request(new Data(NOWON_ID, 2, LocalDateTime.of(2023, 3, 19, 22, 0), null, 111.0, null));
        }

        gotAlert.get(10, TimeUnit.SECONDS);
        List<String> snapshot;
        synchronized (received) {
            snapshot = List.copyOf(received);
        }
        assertThat(snapshot).anyMatch(line -> line.startsWith("event:measurement"));
        assertThat(snapshot).anyMatch(line -> line.startsWith("event:maintenance"));
        assertThat(snapshot).anyMatch(line -> line.contains("\"grade\":3") && line.contains("\"sustainedHours\":2"));
        streaming.cancel(true);
    }
}
