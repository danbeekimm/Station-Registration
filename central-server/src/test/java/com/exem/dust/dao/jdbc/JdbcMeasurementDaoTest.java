package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.dao.StationDao;
import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.TokenHasher;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMeasurementDaoTest extends IntegrationTestSupport {

    @Autowired
    MeasurementDao measurementDao;

    @Autowired
    StationDao stationDao;

    @Test
    void seedStationsArePresentWithHashedTokens() {
        var nowon = stationDao.findById("111311").orElseThrow();

        assertThat(nowon.name()).isEqualTo("노원구");
        assertThat(nowon.tokenHash()).hasSize(64).doesNotContain("dev-token");
        assertThat(TokenHasher.matches(nowon.tokenHash(), nowon.id(), "dev-token-nowon-111311")).isTrue();
        assertThat(stationDao.findAll()).extracting("id").contains("111311", "111121", "111212");
    }

    @Test
    void insertIfAbsentIsIdempotentPerMeasuredAt() {
        var first = new Measurement("111311", 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null);
        var resent = new Measurement("111311", 1, LocalDateTime.of(2023, 3, 1, 0, 0), 23.0, 11.0, null);
        var withNulls = new Measurement("111311", 2, LocalDateTime.of(2023, 3, 1, 1, 0), null, null, null);

        assertThat(measurementDao.insertIfAbsent(first)).isTrue();
        assertThat(measurementDao.insertIfAbsent(resent)).isFalse();
        assertThat(measurementDao.insertIfAbsent(withNulls)).isTrue();

        assertThat(measurementDao.maxSeq("111311")).isEqualTo(2);
        assertThat(measurementDao.maxSeq("111121")).isZero();
        assertThat(measurementDao.findAllOrderedByTime("111311")).containsExactly(first, withNulls);
    }
}
