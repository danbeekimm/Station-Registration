package com.exem.dust.report.dto;

import java.time.LocalDateTime;

/** 기준 이상이 연속된 구간. 시작·종료 시각 포함. */
public record ThresholdStreak(LocalDateTime start, LocalDateTime end, int hours) {

    public static final ThresholdStreak NONE = new ThresholdStreak(null, null, 0);

    /** 한 시간이라도 기준 이상이 있었는지. */
    public boolean exists() {
        return hours > 0;
    }
}
