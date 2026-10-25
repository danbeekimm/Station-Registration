package com.exem.dust.service.dto;

import com.exem.dust.domain.measurement.Measurement;

/** 이미 저장된 시각. 판정하지 않았고 ACK만 다시 보낸다. */
public record MeasurementDuplicate(Measurement measurement) implements IngestResult {
}
