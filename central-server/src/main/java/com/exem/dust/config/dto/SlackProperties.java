package com.exem.dust.config.dto;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Slack Incoming Webhook 설정({@code dust.slack.*}). webhookUrl이 비어 있으면 전송을 건너뛰고 경보는 DB에만 남는다(slack_status=SKIPPED).
 *
 * @param autoDispatch false면 백그라운드 디스패처를 띄우지 않는다. 테스트가 dispatchOne()을 직접 호출할 때 쓴다
 * @param dashboardUrl 메시지에 넣을 대시보드 주소. 비어 있으면 링크 줄을 생략한다
 */
public record SlackProperties(
        @DefaultValue("") String webhookUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration sendInterval,
        @DefaultValue("true") boolean autoDispatch,
        @DefaultValue("") String dashboardUrl) {

    /** Webhook URL이 있어야 전송한다. 없으면 디스패처가 PENDING을 SKIPPED로 정리한다. */
    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /** URL은 비밀에 가까우므로 로그에는 설정 여부만 남긴다. */
    @Override
    public String toString() {
        return "SlackProperties[webhookUrl=%s, maxAttempts=%d, sendInterval=%s]".formatted(isConfigured() ? "(설정됨)" : "(없음)", maxAttempts, sendInterval);
    }
}
