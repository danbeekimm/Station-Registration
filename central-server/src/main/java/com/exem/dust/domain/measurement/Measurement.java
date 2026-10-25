package com.exem.dust.domain.measurement;

import java.time.LocalDateTime;

/** 저장 단위의 측정값 1건. 농도 null은 해당 장비 점검을 뜻한다. */
public record Measurement(
        String stationId,
        int seq,
        LocalDateTime measuredAt,
        Double pm10,
        Double pm25,
        Double o3) {
}
