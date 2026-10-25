package com.exem.dust.service.ingest;

import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.Station;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 평온 시각 이후만 재생한 상태가 전체 재생과 같은지 확인한다. */
class StationStateLoaderTest extends IntegrationTestSupport {

    private static final String STATION = "111311";
    private static final LocalDateTime T0 = LocalDateTime.of(2023, 3, 23, 18, 0);

    @Autowired
    StationStateLoader loader;
    @Autowired
    MeasurementDao measurementDao;

    @Test
    void partialReplayFromCalmHourMatchesFullReplay() {
        var history = List.of(
                new Measurement(STATION, 1, T0, 160.0, 20.0, null),                 // 고농도지만 뒤에 평온 시각이 있어 무시된다
                new Measurement(STATION, 2, T0.plusHours(1), 160.0, 20.0, null),
                new Measurement(STATION, 3, T0.plusHours(2), 40.0, 20.0, null),     // 평온 ← 재생 시작점
                new Measurement(STATION, 4, T0.plusHours(3), null, 20.0, null),     // PM10 점검 시작
                new Measurement(STATION, 5, T0.plusHours(4), 170.0, 90.0, null),
                new Measurement(STATION, 6, T0.plusHours(5), 170.0, 90.0, null));   // PM2.5 주의보 2시간, PM10도 2시간
        history.forEach(measurementDao::insertIfAbsent);

        var restored = loader.load(STATION);
        var fullReplay = new Station(STATION);
        history.forEach(fullReplay::accept);

        var next = new Measurement(STATION, 7, T0.plusHours(6), 170.0, 90.0, null);
        assertThat(restored.lastMeasuredAt()).isEqualTo(fullReplay.lastMeasuredAt());
        assertThat(restored.accept(next)).isEqualTo(fullReplay.accept(next));
        assertThat(restored.accept(new Measurement(STATION, 8, T0.plusHours(7), 170.0, 90.0, null)).alert())
                .get().extracting("sustainedHours").isEqualTo(4);
    }

    @Test
    void fallsBackToFullReplayWhenNoCalmHourExists() {
        measurementDao.insertIfAbsent(new Measurement(STATION, 1, T0, 160.0, 20.0, null));
        measurementDao.insertIfAbsent(new Measurement(STATION, 2, T0.plusHours(1), 160.0, 20.0, null));

        var restored = loader.load(STATION);

        assertThat(restored.accept(new Measurement(STATION, 3, T0.plusHours(2), 160.0, 20.0, null)).alert())
                .get().extracting("sustainedHours").isEqualTo(3);
    }

    @Test
    void emptyHistoryGivesFreshState() {
        var restored = loader.load(STATION);

        assertThat(restored.lastMeasuredAt()).isEmpty();
        assertThat(restored.accept(new Measurement(STATION, 1, T0, 160.0, 20.0, null)).alert()).isEmpty();
    }
}
