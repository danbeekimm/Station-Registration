package com.exem.dust.config.dto;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * DATA 의미 검증 한계({@code dust.validation.*}). 농도 상한은 국내 관측 기록을 넉넉히 넘는 값으로,
 * 센서 오작동이나 조작된 값이 경보를 내는 것을 막는다.
 *
 * @param maxFutureSkew 측정 시각이 서버 시각보다 이만큼 이상 미래면 거부
 */
public record ValidationProperties(
        @DefaultValue("2000") double maxDustConcentration,
        @DefaultValue("1.0") double maxOzonePpm,
        @DefaultValue("1d") Duration maxFutureSkew,
        @DefaultValue("3") int maxConsecutiveOutOfOrder) {
}
