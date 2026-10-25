package com.exem.dust.simulator;

import java.time.Duration;

/** 재접속 대기 시간. 실패할 때마다 두 배로 늘고 상한에서 멈추며, 접속에 성공하면 처음으로 돌아간다. */
final class ReconnectBackoff {

    private final Duration initial;
    private final Duration max;
    private Duration next;

    ReconnectBackoff(Duration initial, Duration max) {
        this.initial = initial;
        this.max = max;
        this.next = initial;
    }

    Duration nextDelay() {
        var current = next;
        var doubled = next.multipliedBy(2);
        next = doubled.compareTo(max) > 0 ? max : doubled;
        return current;
    }

    void reset() {
        next = initial;
    }
}
