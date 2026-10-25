package com.exem.dust.simulator.transport;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 고정 주기 송신 시각표. n번째 건의 송신 시각은 시작 시각 + n·간격으로 미리 정해진다.
 * ACK 대기나 처리 지연이 있어도 다음 송신 시각이 밀리지 않아 "1초 간격"이 누적 오차 없이 유지된다.
 */
final class FixedRateSchedule {

    private final long startNanos;
    private final long intervalNanos;
    private final LongSupplier nanoClock;

    FixedRateSchedule(Duration interval) {
        this(interval, System::nanoTime);
    }

    FixedRateSchedule(Duration interval, LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.intervalNanos = interval.toNanos();
        this.startNanos = nanoClock.getAsLong();
    }

    /** slotIndex번째 송신 시각까지 남은 시간. 이미 지났으면 0이다. */
    Duration remainingUntil(long slotIndex) {
        long remaining = startNanos + slotIndex * intervalNanos - nanoClock.getAsLong();
        return remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO;
    }

    /** slotIndex번째 송신 시각까지 잔다. 이미 지났으면 바로 돌아온다. */
    void awaitSlot(long slotIndex) throws InterruptedException {
        var remaining = remainingUntil(slotIndex);
        if (!remaining.isZero()) {
            Thread.sleep(remaining);
        }
    }
}
