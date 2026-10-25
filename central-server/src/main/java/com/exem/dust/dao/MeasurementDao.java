package com.exem.dust.dao;

import com.exem.dust.domain.measurement.Measurement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeasurementDao {

    /** (station_id, measured_at)이 이미 있으면 저장하지 않고 false. 재전송·재접속 중복을 멱등 처리한다. */
    boolean insertIfAbsent(Measurement measurement);

    /** 저장된 마지막 seq. 없으면 0. 재접속 시 재개 지점(+1) 계산에 쓴다. */
    int maxSeq(String stationId);

    List<Measurement> findAllOrderedByTime(String stationId);

    /** 지정 시각(포함) 이후의 측정값. 시각 오름차순. */
    List<Measurement> findFromInclusive(String stationId, LocalDateTime from);

    /**
     * 결측이 없고 모든 농도가 주어진 기준 미만인 가장 최근 측정 시각.
     * 그 시각을 처리하면 룰별 지속 카운터가 모두 0이 되고 진행 중 점검도 없으므로, 그 시각부터만 재생해도 판정 상태가 전체 재생과 같다.
     * 그런 시각이 없으면(처음부터 계속 고농도·결측) 비어 있다.
     */
    Optional<LocalDateTime> latestCalmHour(String stationId, double pm10Below, double pm25Below, double o3Below);
}
