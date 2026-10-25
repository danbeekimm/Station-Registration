package com.exem.dust.dao.jdbc;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 기동 시 DB를 준비한다. 두 단계다.
 * <ol>
 *   <li>db/schema.sql — 테이블·인덱스를 보장하고 필수 테이블이 모두 있는지 확인한다. 없으면 기동을 중단한다.</li>
 *   <li>db/seed.sql — 초기 측정소 3곳을 등록한다. 이미 있으면 건드리지 않는다.</li>
 * </ol>
 * 두 스크립트 모두 IF NOT EXISTS / ON CONFLICT DO NOTHING만 쓰므로 두 번째 기동부터는 아무것도 바꾸지 않는다.
 * 시드는 빈 DB를 처음 채우는 역할만 하고, 이후 측정소 변경은 DB(registerStation)로만 한다.
 * 싱글턴 초기화 단계에서 실행되므로 TCP 수신이 열리기 전에 끝난다.
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final String SCHEMA_SCRIPT = "db/schema.sql";
    private static final String SEED_SCRIPT = "db/seed.sql";
    static final List<String> REQUIRED_TABLES = List.of(
            "station", "measurement", "maintenance_log", "alert_log", "connection_reject_log");

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public DatabaseInitializer(DataSource dataSource, JdbcClient jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        initializeSchema();
        seedStations();
    }

    /** schema.sql을 실행한 뒤 필수 테이블이 모두 있는지 확인한다. 하나라도 없으면 예외로 기동을 멈춘다. */
    void initializeSchema() {
        runScript(SCHEMA_SCRIPT);
        var present = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name IN (:tables)
                """)
                .param("tables", REQUIRED_TABLES)
                .query(String.class)
                .list();
        var missing = REQUIRED_TABLES.stream().filter(table -> !present.contains(table)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("스키마 초기화 후에도 테이블이 없습니다: " + missing);
        }
        log.info("스키마 확인 완료: 테이블 {}개 {}", present.size(), present);
    }

    /** seed.sql을 실행한다. 신규로 들어간 건수는 실행 전후의 행 수 차이로 센다. */
    void seedStations() {
        long before = stationCount();
        runScript(SEED_SCRIPT);
        long after = stationCount();
        log.info("측정소 시드 반영: 신규 {}건, 기존 {}건 (등록 측정소 {}곳)", after - before, before, after);
    }

    private void runScript(String classpathLocation) {
        var populator = new ResourceDatabasePopulator(new ClassPathResource(classpathLocation));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    private long stationCount() {
        return jdbc.sql("SELECT count(*) FROM station").query(Long.class).single();
    }
}
