package com.exem.dust.service.ingest.dto;

import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.dto.StationEvaluation;

/** 새로 저장했고 판정까지 끝났다. */
public record MeasurementStored(Measurement measurement, StationEvaluation evaluation) implements IngestResult {
}
