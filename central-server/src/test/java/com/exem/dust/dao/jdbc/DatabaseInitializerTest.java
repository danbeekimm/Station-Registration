package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.StationDao;
import com.exem.dust.domain.station.TokenHasher;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DatabaseInitializerTest extends IntegrationTestSupport {

    @Autowired
    DatabaseInitializer initializer;

    @Autowired
    StationDao stationDao;

    @Test
    void createsAllTablesOnStartup() {
        var tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()")
                .query(String.class).list();

        assertThat(tables).containsAll(DatabaseInitializer.REQUIRED_TABLES);
    }

    @Test
    void rerunningOnExistingSchemaKeepsData() {
        jdbc.sql("INSERT INTO measurement (station_id, seq, measured_at, pm10, pm25) VALUES ('111311', 1, '2023-03-01 00:00', 23, 11)").update();

        assertThatCode(initializer::initialize).doesNotThrowAnyException();

        assertThat(jdbc.sql("SELECT count(*) FROM measurement").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void seedRegistersInitialStationsWithHashesMatchingTheJavaHasher() {
        // SQL의 encode(sha256(...), 'hex')가 TokenHasher와 같은 값을 내야 시뮬레이터의 개발용 토큰이 인증된다
        assertThat(stationDao.findAll()).extracting("id").contains("111311", "111121", "111212");
        assertThat(TokenHasher.matches(stationDao.findById("111311").orElseThrow().tokenHash(), "111311", "dev-token-nowon-111311")).isTrue();
        assertThat(TokenHasher.matches(stationDao.findById("111121").orElseThrow().tokenHash(), "111121", "dev-token-junggu-111121")).isTrue();
        assertThat(TokenHasher.matches(stationDao.findById("111212").orElseThrow().tokenHash(), "111212", "dev-token-gangseo-111212")).isTrue();
    }

    @Test
    void seedNeverOverwritesExistingStations() {
        long before = jdbc.sql("SELECT count(*) FROM station").query(Long.class).single();
        jdbc.sql("UPDATE station SET name = '운영 중 변경' WHERE station_id = '111121'").update();
        try {
            initializer.initialize();

            assertThat(stationDao.findById("111121").orElseThrow().name()).isEqualTo("운영 중 변경");
            assertThat(jdbc.sql("SELECT count(*) FROM station").query(Long.class).single()).isEqualTo(before);
        } finally {
            jdbc.sql("UPDATE station SET name = '중구' WHERE station_id = '111121'").update();
        }
    }
}
