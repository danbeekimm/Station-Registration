package com.exem.dust.notify.slack;

import com.exem.dust.config.DustProperties;
import com.exem.dust.config.dto.SlackProperties;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.notify.slack.dto.PendingAlert;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlackMessageFormatterTest {

    private static DustProperties propertiesWithDashboard(String dashboardUrl) {
        return new DustProperties(null, null,
                new SlackProperties("https://hooks.slack.test/x", Duration.ofSeconds(3), Duration.ofSeconds(5), 3,
                        Duration.ofSeconds(1), true, dashboardUrl),
                List.of());
    }

    @Test
    void warningGradeUsesRedSirenAndIncludesDashboardLink() {
        var formatter = new SlackMessageFormatter(propertiesWithDashboard("http://localhost:8080"));
        var pending = new PendingAlert(7L, "노원구",
                new AlertIssued("111311", LocalDateTime.of(2023, 3, 23, 22, 0), AlertRule.PM10_WARNING, 313.0, 2), 0);

        var payload = formatter.format(pending);

        assertThat(payload)
                .contains(":rotating_light: [미세먼지 경보 · 2등급] 노원구(111311)")
                .contains("측정 시각 2023-03-23 22:00")
                .contains("PM10 313 ㎍/㎥")
                .contains("2시간 연속 기준 이상")
                .contains("\"color\":\"#D0021B\"")
                .contains("http://localhost:8080/#111311")
                .contains("\"type\":\"mrkdwn\"");
    }

    @Test
    void watchGradeUsesOrangeWarningAndOmitsLinkWhenNoDashboard() {
        var formatter = new SlackMessageFormatter(propertiesWithDashboard(""));
        var pending = new PendingAlert(8L, "중구",
                new AlertIssued("111121", LocalDateTime.of(2023, 3, 20, 2, 0), AlertRule.PM25_WATCH, 114.0, 7), 1);

        var payload = formatter.format(pending);

        assertThat(payload)
                .contains(":warning: [초미세먼지 주의보 · 3등급] 중구(111121)")
                .contains("\"color\":\"#F5A623\"")
                .doesNotContain("대시보드");
    }

    @Test
    void ozoneKeepsDecimalValueAndUnit() {
        var formatter = new SlackMessageFormatter(propertiesWithDashboard(""));
        var pending = new PendingAlert(9L, "강서구",
                new AlertIssued("111212", LocalDateTime.of(2023, 3, 1, 15, 0), AlertRule.O3_WATCH, 0.12, 1), 0);

        assertThat(formatter.format(pending)).contains("O3 0.12 ppm").contains(":large_blue_circle:");
    }
}
