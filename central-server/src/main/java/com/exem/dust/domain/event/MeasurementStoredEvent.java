package com.exem.dust.domain.event;

import com.exem.dust.domain.measurement.Measurement;

/** 측정값 1건이 저장됐다. 커밋 후 전달되어 대시보드 스트림이 구독한다. */
public record MeasurementStoredEvent(Measurement measurement) {
}
