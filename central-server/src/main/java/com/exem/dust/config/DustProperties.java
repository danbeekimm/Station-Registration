package com.exem.dust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** application.yml의 {@code dust.*} 설정. 환경변수와 실행 인자로 덮어쓸 수 있다. */
@ConfigurationProperties(prefix = "dust")
public record DustProperties(Tcp tcp, @DefaultValue Validation validation, @DefaultValue List<StationSeed> stations) {

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
