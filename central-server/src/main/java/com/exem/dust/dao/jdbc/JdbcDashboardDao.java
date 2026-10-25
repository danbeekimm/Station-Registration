package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.DashboardDao;
import com.exem.dust.web.dto.AlertRow;
import com.exem.dust.web.dto.ChartPoint;
import com.exem.dust.web.dto.MaintenanceRow;
import com.exem.dust.web.dto.StationCard;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.exem.dust.dao.jdbc.JdbcMeasurementDao.nullableDouble;

@Repository
public class JdbcDashboardDao implements DashboardDao {

    private static final RowMapper<ChartPoint> POINT_MAPPER = (resultSet, rowNumber) -> new ChartPoint(
            resultSet.getObject("measured_at", LocalDateTime.class),
            nullableDouble(resultSet, "pm10"),
            nullableDouble(resultSet, "pm25"));

    private static final RowMapper<AlertRow> ALERT_MAPPER = (resultSet, rowNumber) -> new AlertRow(
            resultSet.getString("station_id"),
            resultSet.getString("name"),
            resultSet.getObject("issued_at", LocalDateTime.class),
            resultSet.getInt("grade"),
            resultSet.getString("level"),
            resultSet.getString("pollutant"),
            resultSet.getBigDecimal("trigger_value").doubleValue(),
            resultSet.getInt("sustained_hours"),
            resultSet.getString("slack_status"));

    private static final RowMapper<MaintenanceRow> MAINTENANCE_MAPPER = (resultSet, rowNumber) -> new MaintenanceRow(
            resultSet.getString("station_id"),
            resultSet.getString("name"),
            resultSet.getString("equipment"),
            resultSet.getObject("started_at", LocalDateTime.class),
            resultSet.getObject("ended_at", LocalDateTime.class),
            resultSet.getInt("hours"));

    private final JdbcClient jdbc;

    public JdbcDashboardDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StationCard> stationCards(int chartPoints) {
        // LATERAL 서브쿼리로 측정소마다 최신 측정값·마지막 경보를 한 번에 읽는다
        var cards = jdbc.sql("""
                SELECT s.station_id, s.name,
                       latest.measured_at AS latest_at, latest.pm10, latest.pm25,
                       last_alert.issued_at AS last_alert_at, last_alert.grade AS last_grade, last_alert.level AS last_level,
                       (SELECT string_agg(ml.equipment, ',' ORDER BY ml.equipment)
                          FROM maintenance_log ml
                         WHERE ml.station_id = s.station_id AND ml.ended_at IS NULL) AS open_maintenance,
                       (SELECT count(*) FROM alert_log al WHERE al.station_id = s.station_id) AS alert_count
                FROM station s
                LEFT JOIN LATERAL (SELECT measured_at, pm10, pm25 FROM measurement m
                                    WHERE m.station_id = s.station_id ORDER BY measured_at DESC LIMIT 1) latest ON true
                LEFT JOIN LATERAL (SELECT issued_at, grade, level FROM alert_log a
                                    WHERE a.station_id = s.station_id ORDER BY issued_at DESC LIMIT 1) last_alert ON true
                ORDER BY s.station_id
                """)
                .query(this::mapCardWithoutPoints)
                .list();
        var withPoints = new ArrayList<StationCard>(cards.size());
        for (var card : cards) {
            withPoints.add(new StationCard(card.id(), card.name(), card.latestAt(), card.pm10(), card.pm25(), card.lastGrade(),
                    card.lastLevel(), card.lastAlertAt(), card.openMaintenance(), card.alertCount(), recentPoints(card.id(), chartPoints)));
        }
        return withPoints;
    }

    private StationCard mapCardWithoutPoints(ResultSet resultSet, int rowNumber) throws SQLException {
        var openMaintenance = resultSet.getString("open_maintenance");
        return new StationCard(
                resultSet.getString("station_id"),
                resultSet.getString("name"),
                resultSet.getObject("latest_at", LocalDateTime.class),
                nullableDouble(resultSet, "pm10"),
                nullableDouble(resultSet, "pm25"),
                resultSet.getObject("last_grade", Integer.class),
                resultSet.getString("last_level"),
                resultSet.getObject("last_alert_at", LocalDateTime.class),
                openMaintenance == null ? List.of() : List.of(openMaintenance.split(",")),
                resultSet.getLong("alert_count"),
                List.of());
    }

    @Override
    public List<ChartPoint> recentPoints(String stationId, int limit) {
        var newestFirst = jdbc.sql("""
                SELECT measured_at, pm10, pm25 FROM measurement
                WHERE station_id = :stationId
                ORDER BY measured_at DESC
                LIMIT :limit
                """)
                .param("stationId", stationId)
                .param("limit", limit)
                .query(POINT_MAPPER)
                .list();
        return newestFirst.reversed();
    }

    @Override
    public List<AlertRow> recentAlerts(int limit) {
        return jdbc.sql("""
                SELECT a.station_id, s.name, a.issued_at, a.grade, a.level, a.pollutant, a.trigger_value, a.sustained_hours, a.slack_status
                FROM alert_log a JOIN station s ON s.station_id = a.station_id
                ORDER BY a.issued_at DESC, a.id DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(ALERT_MAPPER)
                .list();
    }

    @Override
    public List<MaintenanceRow> recentMaintenance(int limit) {
        return jdbc.sql("""
                SELECT ml.station_id, s.name, ml.equipment, ml.started_at, ml.ended_at, ml.hours
                FROM maintenance_log ml JOIN station s ON s.station_id = ml.station_id
                ORDER BY ml.ended_at IS NOT NULL, ml.started_at DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(MAINTENANCE_MAPPER)
                .list();
    }

    @Override
    public Map<String, Long> rejectCountsByReason() {
        var counts = new LinkedHashMap<String, Long>();
        jdbc.sql("SELECT reason_code, count(*) AS cnt FROM connection_reject_log GROUP BY reason_code ORDER BY cnt DESC")
                .query((resultSet, rowNumber) -> Map.entry(resultSet.getString("reason_code"), resultSet.getLong("cnt")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    @Override
    public long pendingSlackCount() {
        return jdbc.sql("SELECT count(*) FROM alert_log WHERE slack_status = 'PENDING'").query(Long.class).single();
    }
}
