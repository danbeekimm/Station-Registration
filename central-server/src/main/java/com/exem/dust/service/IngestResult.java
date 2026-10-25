package com.exem.dust.service;

import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.Station;

/** 수신 1건 처리 결과. 중복이어도 측정소에는 ACK를 다시 보내므로 호출자가 구분만 할 수 있으면 된다. */
public sealed interface IngestResult {

    record Stored(Measurement measurement, Station.Evaluation evaluation) implements IngestResult {
    }

    record Duplicate(Measurement measurement) implements IngestResult {
    }
}
