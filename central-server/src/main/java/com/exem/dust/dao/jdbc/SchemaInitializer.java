package com.exem.dust.dao.jdbc;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;

/**
 * 기동 시 db/schema.sql을 실행한다. 스크립트가 전부 IF NOT EXISTS라 두 번째 기동부터는 아무것도 바꾸지 않는다.
 * 테이블이 하나라도 없으면 기동을 중단해, 스키마가 어긋난 상태로 수신이 시작되는 일을 막는다.
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private static final String SCHEMA_SCRIPT = "db/schema.sql";
    static final List<String> REQUIRED_TABLES = List.of(
            "station", "measurement", "maintenance_log", "alert_log", "connection_reject_log");

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public SchemaInitializer(DataSource dataSource, JdbcClient jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    /** schema.sql을 실행한 뒤 필수 테이블이 모두 있는지 확인한다. 싱글턴 초기화 단계라 TCP 수신보다 먼저 끝난다. */
    @PostConstruct
    public void initialize() {
        var populator = new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_SCRIPT));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(dataSource);

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
}
