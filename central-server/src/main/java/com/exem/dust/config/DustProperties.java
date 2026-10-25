package com.exem.dust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/** application.yml의 {@code dust.*} 설정. 환경변수와 실행 인자로 덮어쓸 수 있다. */
@ConfigurationProperties(prefix = "dust")
public record DustProperties(Tcp tcp, @DefaultValue List<StationSeed> stations) {

    public record Tcp(
            @DefaultValue("9000") int port,
            @DefaultValue("5s") Duration helloTimeout,
            @DefaultValue("30s") Duration readTimeout,
            @DefaultValue("4096") int maxLineBytes) {
    }

    /** 기동 시 upsert하는 측정소. 토큰은 해시해서 저장하고 평문은 메모리 밖으로 내보내지 않는다. */
    public record StationSeed(String code, String name, String token) {

        @Override
        public String toString() {
            return "StationSeed[code=%s, name=%s, token=****]".formatted(code, name);
        }
    }
}
