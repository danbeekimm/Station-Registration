package com.exem.dust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** application.yml의 {@code dust.*} 설정. 환경변수와 실행 인자로 덮어쓸 수 있다. */
@ConfigurationProperties(prefix = "dust")
public record DustProperties(
        Tcp tcp,
        @DefaultValue Validation validation,
        @DefaultValue Slack slack,
        @DefaultValue List<StationSeed> stations) {

    /**
     * Slack Incoming Webhook 설정. webhookUrl이 비어 있으면 전송을 건너뛰고 경보는 DB에만 남는다(slack_status=SKIPPED).
     *
     * @param autoDispatch  false면 백그라운드 디스패처를 띄우지 않는다. 테스트가 dispatchOne()을 직접 호출할 때 쓴다
     * @param dashboardUrl  메시지에 넣을 대시보드 주소. 비어 있으면 링크 줄을 생략한다
     */
    public record Slack(
            @DefaultValue("") String webhookUrl,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("5s") Duration readTimeout,
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1s") Duration sendInterval,
            @DefaultValue("true") boolean autoDispatch,
            @DefaultValue("") String dashboardUrl) {

        public boolean isConfigured() {
            return webhookUrl != null && !webhookUrl.isBlank();
        }

        @Override
        public String toString() {
            return "Slack[webhookUrl=%s, maxAttempts=%d, sendInterval=%s]".formatted(isConfigured() ? "(설정됨)" : "(없음)", maxAttempts, sendInterval);
        }
    }

    public record Tcp(
            @DefaultValue("9000") int port,
            @DefaultValue("5s") Duration helloTimeout,
            @DefaultValue("30s") Duration readTimeout,
            @DefaultValue("4096") int maxLineBytes) {
    }

    /**
     * DATA 의미 검증 한계. 농도 상한은 국내 관측 기록을 넉넉히 넘는 값으로, 센서 오작동이나 조작된 값이 경보를 내는 것을 막는다.
     *
     * @param maxFutureSkew 측정 시각이 서버 시각보다 이만큼 이상 미래면 거부
     */
    public record Validation(
            @DefaultValue("2000") double maxDustConcentration,
            @DefaultValue("1.0") double maxOzonePpm,
            @DefaultValue("1d") Duration maxFutureSkew,
            @DefaultValue("3") int maxConsecutiveOutOfOrder) {
    }

    /** 기동 시 upsert하는 측정소. 토큰은 해시해서 저장하고 평문은 메모리 밖으로 내보내지 않는다. */
    public record StationSeed(String code, String name, String token) {

        @Override
        public String toString() {
            return "StationSeed[code=%s, name=%s, token=****]".formatted(code, name);
        }
    }
}
