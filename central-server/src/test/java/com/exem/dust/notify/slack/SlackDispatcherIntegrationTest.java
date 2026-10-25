package com.exem.dust.notify.slack;

import com.exem.dust.dao.AlertDao;
import com.exem.dust.dao.SlackOutboxDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.support.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * outbox 상태 전이를 실제 DB로 검증한다. Webhook 호출은 가짜 클라이언트가 응답을 흉내 낸다.
 * URL이 설정된 별도 컨텍스트라 IntegrationTestSupport를 상속하지 않는다.
 */
@SpringBootTest(properties = {
        "dust.tcp.port=0",
        "dust.slack.auto-dispatch=false",
        "dust.slack.webhook-url=https://hooks.slack.test/services/T/B/X",
        "dust.slack.max-attempts=3"})
@Import(PostgresTestContainerConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class SlackDispatcherIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 19, 22, 0);

    @Autowired
    SlackDispatcher dispatcher;
    @Autowired
    AlertDao alertDao;
    @Autowired
    SlackOutboxDao outbox;
    @Autowired
    JdbcClient jdbc;
    @MockitoBean
    SlackWebhookClient webhookClient;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM alert_log").update();
    }

    @Test
    void sendsOldestPendingFirstAndMarksSent() {
        alertDao.insertIfAbsent(alert("111311", T0.plusHours(1), AlertRule.PM25_WATCH, 95.0, 3));
        alertDao.insertIfAbsent(alert("111121", T0, AlertRule.PM25_WATCH, 102.0, 2));
        when(webhookClient.send(anyString(), anyString())).thenReturn(new SendOutcome.Sent());

        assertThat(dispatcher.dispatchOne()).isTrue();
        verify(webhookClient).send(anyString(), contains("중구(111121)"));
        assertThat(dispatcher.dispatchOne()).isTrue();
        assertThat(dispatcher.dispatchOne()).isFalse();

        assertThat(outbox.countByStatus("SENT")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM alert_log WHERE slack_sent_at IS NULL").query(Long.class).single()).isZero();
    }

    @Test
    void failuresBackOffThenGiveUpAfterMaxAttemptsKeepingTheAlertRow() {
        alertDao.insertIfAbsent(alert("111311", T0, AlertRule.PM10_WARNING, 313.0, 2));
        when(webhookClient.send(anyString(), anyString())).thenReturn(new SendOutcome.Failed("HTTP 500 boom"));

        assertThat(dispatcher.dispatchOne()).isTrue();
        Map<String, Object> afterFirst = jdbc.sql("SELECT slack_status, slack_attempts, slack_error, slack_next_at > now() AS deferred FROM alert_log")
                .query().singleRow();
        assertThat(afterFirst).containsEntry("slack_status", "PENDING").containsEntry("slack_attempts", 1)
                .containsEntry("slack_error", "HTTP 500 boom").containsEntry("deferred", true);

        // 백오프 중에는 집어가지 않는다
        assertThat(dispatcher.dispatchOne()).isFalse();

        // 백오프를 지난 것으로 만들어 두 번째·세 번째 시도
        jdbc.sql("UPDATE alert_log SET slack_next_at = now() - interval '1 second'").update();
        assertThat(dispatcher.dispatchOne()).isTrue();
        jdbc.sql("UPDATE alert_log SET slack_next_at = now() - interval '1 second'").update();
        assertThat(dispatcher.dispatchOne()).isTrue();

        Map<String, Object> finalRow = jdbc.sql("SELECT slack_status, slack_attempts, grade FROM alert_log").query().singleRow();
        assertThat(finalRow).containsEntry("slack_status", "FAILED").containsEntry("slack_attempts", 3).containsEntry("grade", 2);
        verify(webhookClient, times(3)).send(anyString(), anyString());
    }

    @Test
    void rateLimitDefersWithoutCountingAnAttempt() {
        alertDao.insertIfAbsent(alert("111311", T0, AlertRule.PM10_WATCH, 160.0, 2));
        when(webhookClient.send(anyString(), anyString())).thenReturn(new SendOutcome.RateLimited(Duration.ofSeconds(30)));

        assertThat(dispatcher.dispatchOne()).isTrue();

        Map<String, Object> row = jdbc.sql("SELECT slack_status, slack_attempts, slack_next_at > now() + interval '20 seconds' AS deferred FROM alert_log")
                .query().singleRow();
        assertThat(row).containsEntry("slack_status", "PENDING").containsEntry("slack_attempts", 0).containsEntry("deferred", true);
        assertThat(dispatcher.dispatchOne()).isFalse();
    }

    @Test
    void restartPicksUpPendingRowsLeftByPreviousRun() {
        // 이전 기동이 남긴 PENDING 행은 메모리에 아무것도 없어도 DB에서 바로 이어진다
        alertDao.insertIfAbsent(alert("111311", T0, AlertRule.PM25_WATCH, 80.0, 2));
        alertDao.insertIfAbsent(alert("111311", T0.plusHours(1), AlertRule.PM25_WATCH, 85.0, 3));
        alertDao.insertIfAbsent(alert("111311", T0.plusHours(2), AlertRule.PM25_WATCH, 90.0, 4));
        when(webhookClient.send(anyString(), anyString())).thenReturn(new SendOutcome.Sent());

        int delivered = 0;
        while (dispatcher.dispatchOne()) {
            delivered++;
        }

        assertThat(delivered).isEqualTo(3);
        assertThat(outbox.countByStatus("PENDING")).isZero();
    }

    @Test
    void sentMessagesAreOrderedByIssuedAtAcrossStations() {
        alertDao.insertIfAbsent(alert("111212", T0.plusHours(2), AlertRule.PM10_WATCH, 160.0, 2));
        alertDao.insertIfAbsent(alert("111311", T0, AlertRule.PM10_WATCH, 160.0, 2));
        alertDao.insertIfAbsent(alert("111121", T0.plusHours(1), AlertRule.PM10_WATCH, 160.0, 2));
        when(webhookClient.send(anyString(), anyString())).thenReturn(new SendOutcome.Sent());

        while (dispatcher.dispatchOne()) {
            // 전부 비울 때까지
        }

        List<String> sentOrder = jdbc.sql("SELECT station_id FROM alert_log ORDER BY slack_sent_at, id").query(String.class).list();
        assertThat(sentOrder).containsExactly("111311", "111121", "111212");
        verify(webhookClient, never()).send(anyString(), contains("\"token\""));
    }

    private static AlertIssued alert(String stationId, LocalDateTime issuedAt, AlertRule rule, double value, int sustained) {
        return new AlertIssued(stationId, issuedAt, rule, value, sustained);
    }
}
