package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.AlertDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcAlertDao implements AlertDao {

    private static final RowMapper<AlertIssued> ROW_MAPPER = (resultSet, rowNumber) -> new AlertIssued(
            resultSet.getString("station_id"),
            resultSet.getObject("issued_at", LocalDateTime.class),
            AlertRule.ofGrade(resultSet.getInt("grade")),
            resultSet.getBigDecimal("trigger_value").doubleValue(),
            resultSet.getInt("sustained_hours"));

    private final JdbcClient jdbc;

    public JdbcAlertDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(AlertIssued alert) {
        // level과 pollutant는 사람이 읽는 용도. 등급↔룰 대응의 단일 출처는 AlertRule enum이다
        int inserted = jdbc.sql("""
                INSERT INTO alert_log (station_id, issued_at, grade, level, pollutant, trigger_value, sustained_hours)
                VALUES (:stationId, :issuedAt, :grade, :level, :pollutant, :triggerValue, :sustainedHours)
                ON CONFLICT (station_id, issued_at) DO NOTHING
                """)
                .param("stationId", alert.stationId())
                .param("issuedAt", alert.issuedAt())
                .param("grade", alert.rule().grade())
                .param("level", alert.rule().label())
                .param("pollutant", alert.rule().pollutant().name())
                .param("triggerValue", alert.triggerValue())
                .param("sustainedHours", alert.sustainedHours())
                .update();
        return inserted == 1;
    }

    @Override
    public List<AlertIssued> findByStationOrderedByTime(String stationId) {
        return jdbc.sql("""
                SELECT station_id, issued_at, grade, trigger_value, sustained_hours
                FROM alert_log
                WHERE station_id = :stationId
                ORDER BY issued_at
                """)
                .param("stationId", stationId)
                .query(ROW_MAPPER)
                .list();
    }
}
