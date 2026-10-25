package com.exem.dust.notify.slack;

import com.exem.dust.config.DustProperties;
import com.exem.dust.domain.alert.PendingAlert;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 경보 1건을 Slack Block Kit payload로 만든다.
 * 경보(1·2등급)는 빨간 바와 :rotating_light:, 주의보(3·4등급)는 주황 바와 :warning:, 오존(5등급)은 파란 바로 구분한다.
 */
@Component
public class SlackMessageFormatter {

    private static final DateTimeFormatter ISSUED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String RED = "#D0021B";
    private static final String ORANGE = "#F5A623";
    private static final String BLUE = "#4A90E2";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String dashboardUrl;

    public SlackMessageFormatter(DustProperties properties) {
        this.dashboardUrl = properties.slack().dashboardUrl();
    }

    /** 경보 1건을 Block Kit payload JSON으로 만든다. text는 알림·미리보기용 평문이다. */
    public String format(PendingAlert pending) {
        var alert = pending.alert();
        var rule = alert.rule();
        var headline = "%s [%s · %d등급] %s(%s)".formatted(emoji(rule.grade()), rule.label(), rule.grade(), pending.stationName(), alert.stationId());
        var detail = "측정 시각 %s  |  %s %s %s  |  %d시간 연속 기준 이상".formatted(
                ISSUED_AT.format(alert.issuedAt()), rule.pollutant().label(), formatValue(alert.triggerValue()), rule.pollutant().unit(), alert.sustainedHours());
        var body = new StringBuilder("*").append(headline).append("*\n").append(detail);
        if (dashboardUrl != null && !dashboardUrl.isBlank()) {
            body.append("\n대시보드 → ").append(dashboardUrl).append("/#").append(alert.stationId());
        }

        Map<String, Object> payload = Map.of(
                "text", headline + " - " + detail,  // 알림·미리보기용 평문
                "attachments", List.of(Map.of(
                        "color", color(rule.grade()),
                        "blocks", List.of(Map.of(
                                "type", "section",
                                "text", Map.of("type", "mrkdwn", "text", body.toString()))))));
        return mapper.writeValueAsString(payload);
    }

    /** 경보(1·2)는 사이렌, 주의보(3·4)는 경고, 오존(5)은 파란 원. */
    private static String emoji(int grade) {
        return grade <= 2 ? ":rotating_light:" : grade <= 4 ? ":warning:" : ":large_blue_circle:";
    }

    /** 등급대별 색상 바. */
    private static String color(int grade) {
        return grade <= 2 ? RED : grade <= 4 ? ORANGE : BLUE;
    }

    /** 농도는 소수점이 의미 없으면 정수로, 오존 ppm처럼 작은 값은 소수 그대로 보인다. */
    private static String formatValue(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
