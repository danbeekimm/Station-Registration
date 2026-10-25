package com.exem.dust.simulator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FixedRateScheduleTest {

    @Test
    void slotTimesAreAnchoredToStartNotToPreviousSend() {
        var clock = new AtomicLong(0);
        var schedule = new FixedRateSchedule(Duration.ofSeconds(1), clock::get);

        // 첫 건 처리가 0.7초 걸렸어도 두 번째 송신 시각은 시작 + 1초 그대로다
        clock.set(Duration.ofMillis(700).toNanos());
        assertThat(schedule.remainingUntil(1)).isEqualTo(Duration.ofMillis(300));

        // 세 번째 건 직전에 2.4초가 흘렀다면 이미 지난 시각이므로 기다리지 않는다
        clock.set(Duration.ofMillis(2400).toNanos());
        assertThat(schedule.remainingUntil(2)).isZero();
        assertThat(schedule.remainingUntil(3)).isEqualTo(Duration.ofMillis(600));
    }

    @Test
    void backoffDoublesUntilCapAndResets() {
        var backoff = new ReconnectBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(16));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(30));

        backoff.reset();
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
    }
}
