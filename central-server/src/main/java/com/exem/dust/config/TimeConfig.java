package com.exem.dust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 현재 시각을 빈으로 두어 검증·디스패처가 테스트에서 시각을 고정할 수 있게 한다. */
@Configuration
public class TimeConfig {

    /** 시스템 시계. 테스트는 Clock.fixed로 바꿔 끼운다. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
