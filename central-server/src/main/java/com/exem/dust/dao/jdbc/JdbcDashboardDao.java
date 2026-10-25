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

    /** 카드 요약 한 쿼리 + 차트 점 한 쿼리. 측정소마다 LATERAL로 인덱스에서 최신 행만 꺼내므로 전체 스캔이 없다. */
    @Override
    public List<StationCard> stationCards(int chartPoints) {
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
        var pointsByStation = recentPointsByStation(chartPoints);
        var withPoints = new ArrayList<StationCard>(cards.size());
        for (var card : cards) {
            withPoints.add(new StationCard(card.id(), card.name(), card.latestAt(), card.pm10(), card.pm25(), card.lastGrade(),
                    card.lastLevel(), card.lastAlertAt(), card.openMaintenance(), card.alertCount(),
                    pointsByStation.getOrDefault(card.id(), List.of())));
        }
        return withPoints;
    }

    /** 차트 점을 뺀 카드 매핑. 진행 중 점검은 쉼표로 이어진 문자열을 목록으로 푼다. */
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

    /** 측정소마다 LATERAL로 최신 N개를 (station_id, measured_at) 인덱스에서 바로 꺼낸다. 한 번의 왕복, 전체 스캔 없음. */
    @Override
    public Map<String, List<ChartPoint>> recentPointsByStation(int limit) {
        var byStation = new LinkedHashMap<String, List<ChartPoint>>();
        jdbc.sql("""
                SELECT s.station_id, recent.measured_at, recent.pm10, recent.pm25
                FROM station s
                CROSS JOIN LATERAL (SELECT measured_at, pm10, pm25 FROM measurement m
                                     WHERE m.station_id = s.station_id ORDER BY measured_at DESC LIMIT :limit) recent
                ORDER BY s.station_id, recent.measured_at
                """)
                .param("limit", limit)
                .query((resultSet, rowNumber) -> Map.entry(resultSet.getString("station_id"), new ChartPoint(
                        resultSet.getObject("measured_at", LocalDateTime.class),
                        nullableDouble(resultSet, "pm10"),
                        nullableDouble(resultSet, "pm25"))))
                .list()
                .forEach(entry -> byStation.computeIfAbsent(entry.getKey(), stationId -> new ArrayList<>()).add(entry.getValue()));
        return byStation;
    }

    /** 경보 피드. 발령 시각 최신순. */
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

    /** 점검 피드. 시작 시각 최신순(started_at 인덱스). 진행 중인 구간은 가장 최근에 시작된 것이라 자연히 상단에 온다. */
    @Override
    public List<MaintenanceRow> recentMaintenance(int limit) {
        return jdbc.sql("""
                SELECT ml.station_id, s.name, ml.equipment, ml.started_at, ml.ended_at, ml.hours
                FROM maintenance_log ml JOIN station s ON s.station_id = ml.station_id
                ORDER BY ml.started_at DESC, ml.equipment
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(MAINTENANCE_MAPPER)
                .list();
    }

    /** 거부 사유별 누적. 많은 순으로 정렬된 맵. */
    @Override
    public Map<String, Long> rejectCountsByReason() {
        var counts = new LinkedHashMap<String, Long>();
        jdbc.sql("SELECT reason_code, count(*) AS cnt FROM connection_reject_log GROUP BY reason_code ORDER BY cnt DESC")
                .query((resultSet, rowNumber) -> Map.entry(resultSet.getString("reason_code"), resultSet.getLong("cnt")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /** 아직 보내지 못한 Slack 메시지 수. PENDING 부분 인덱스로 센다. */
    @Override
    public long pendingSlackCount() {
        return jdbc.sql("SELECT count(*) FROM alert_log WHERE slack_status = 'PENDING'").query(Long.class).single();
    }
}
