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

    /** 이번에 기다릴 시간을 돌려주고, 다음 대기 시간은 두 배(상한까지)로 올린다. */
    Duration nextDelay() {
        var current = next;
        var doubled = next.multipliedBy(2);
        next = doubled.compareTo(max) > 0 ? max : doubled;
        return current;
    }

    /** 접속에 성공하면 처음 대기 시간으로 되돌린다. */
    void reset() {
        next = initial;
    }
}
