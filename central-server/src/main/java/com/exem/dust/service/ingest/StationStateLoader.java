package com.exem.dust.service.ingest;

import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.measurement.Pollutant;
import com.exem.dust.domain.station.Station;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속 인증 직후 측정소의 판정 상태를 DB에서 복원한다. 저장된 측정값을 시각순으로 다시 accept()하면
 * 지속 카운터·직전 시각·진행 중 점검이 정확히 재현되고, 서버 재기동과 측정소 재접속이 같은 경로를 탄다.
 * <p>재생 범위는 "마지막 평온 시각"부터다. 모든 농도가 가장 낮은 기준 미만이고 결측도 없는 시각을 처리하면 룰별 카운터가 모두 0이 되고
 * 진행 중 점검도 닫히므로, 그 시각부터만 재생해도 전체 재생과 결과가 같다. 이력이 수년치로 늘어도 재생량은 최근 고농도·점검 구간 길이에만 비례한다.
 */
@Service
public class StationStateLoader {

    private static final Logger log = LoggerFactory.getLogger(StationStateLoader.class);

    /** 물질별 가장 낮은 기준. 이 값 미만이면 어느 룰의 카운터도 0이다. 기준이 없는 물질은 무한대라 조건에서 빠진다. */
    private static final double PM10_CALM_BELOW = lowestThreshold(Pollutant.PM10);
    private static final double PM25_CALM_BELOW = lowestThreshold(Pollutant.PM25);
    private static final double O3_CALM_BELOW = lowestThreshold(Pollutant.O3);

    private final MeasurementDao measurementDao;

    public StationStateLoader(MeasurementDao measurementDao) {
        this.measurementDao = measurementDao;
    }

    /** 평온 시각이 있으면 그 시각부터, 없으면(처음부터 계속 고농도·결측) 전체를 다시 accept()해 상태를 만든다. */
    @Transactional(readOnly = true)
    public Station load(String stationId) {
        var station = new Station(stationId);
        var calmHour = measurementDao.latestCalmHour(stationId, PM10_CALM_BELOW, PM25_CALM_BELOW, O3_CALM_BELOW);
        var history = calmHour
                .map(from -> measurementDao.findFromInclusive(stationId, from))
                .orElseGet(() -> measurementDao.findAllOrderedByTime(stationId));
        history.forEach(station::accept);
        log.info("[{}] 측정값 {}건을 재생해 판정 상태 복원 (재생 시작 {}, 마지막 측정 시각 {})", stationId, history.size(),
                calmHour.map(Object::toString).orElse("처음부터"), station.lastMeasuredAt().map(Object::toString).orElse("없음"));
        return station;
    }

    private static double lowestThreshold(Pollutant pollutant) {
        return AlertRule.BY_SEVERITY.stream()
                .filter(rule -> rule.pollutant() == pollutant)
                .mapToDouble(AlertRule::threshold)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }
}
