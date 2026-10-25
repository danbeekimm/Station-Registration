package com.exem.dust.dao.jdbc;

import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SchemaInitializerTest extends IntegrationTestSupport {

    @Autowired
    SchemaInitializer schemaInitializer;

    @Test
    void createsAllTablesOnStartup() {
        var tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()")
                .query(String.class).list();

        assertThat(tables).containsAll(SchemaInitializer.REQUIRED_TABLES);
    }

    @Test
    void rerunningOnExistingSchemaKeepsData() {
        jdbc.sql("INSERT INTO measurement (station_id, seq, measured_at, pm10, pm25) VALUES ('111311', 1, '2023-03-01 00:00', 23, 11)").update();

        assertThatCode(schemaInitializer::initialize).doesNotThrowAnyException();

        assertThat(jdbc.sql("SELECT count(*) FROM measurement").query(Long.class).single()).isEqualTo(1L);
    }
}
