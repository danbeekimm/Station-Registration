package com.exem.dust.service;

import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.station.Station;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속 인증 직후 측정소의 판정 상태를 DB에서 복원한다.
 * 저장된 측정값을 시각순으로 다시 accept()하면 지속 카운터·직전 시각·진행 중 점검이 정확히 재현된다.
 * 측정소당 한 달치 744행이라 비용이 미미하고, 서버 재기동과 측정소 재접속이 같은 경로를 탄다.
 */
@Service
public class StationStateLoader {

    private static final Logger log = LoggerFactory.getLogger(StationStateLoader.class);

    private final MeasurementDao measurementDao;

    public StationStateLoader(MeasurementDao measurementDao) {
        this.measurementDao = measurementDao;
    }

    /** 저장된 측정값을 시각순으로 다시 accept()해 상태를 만든다. 저장된 것이 없으면 빈 상태. */
    @Transactional(readOnly = true)
    public Station load(String stationId) {
        var station = new Station(stationId);
        var history = measurementDao.findAllOrderedByTime(stationId);
        history.forEach(station::accept);
        log.info("[{}] 측정값 {}건을 재생해 판정 상태 복원 (마지막 측정 시각 {})",
                stationId, history.size(), station.lastMeasuredAt().map(Object::toString).orElse("없음"));
        return station;
    }
}
