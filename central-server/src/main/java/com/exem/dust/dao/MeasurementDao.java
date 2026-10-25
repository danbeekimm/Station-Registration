package com.exem.dust.dao;

import com.exem.dust.domain.measurement.Measurement;

import java.util.List;

public interface MeasurementDao {

    /** (station_id, measured_at)이 이미 있으면 저장하지 않고 false. 재전송·재접속 중복을 멱등 처리한다. */
    boolean insertIfAbsent(Measurement measurement);

    /** 저장된 마지막 seq. 없으면 0. 재접속 시 재개 지점(+1) 계산에 쓴다. */
    int maxSeq(String stationId);

    List<Measurement> findAllOrderedByTime(String stationId);
}
