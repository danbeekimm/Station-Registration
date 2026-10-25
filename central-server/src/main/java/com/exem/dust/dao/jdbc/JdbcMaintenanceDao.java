package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.MaintenanceDao;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcMaintenanceDao implements MaintenanceDao {

    private static final RowMapper<MaintenanceWindow> ROW_MAPPER = (resultSet, rowNumber) -> new MaintenanceWindow(
            resultSet.getString("station_id"),
            Equipment.valueOf(resultSet.getString("equipment")),
            resultSet.getObject("started_at", LocalDateTime.class),
            resultSet.getObject("ended_at", LocalDateTime.class),
            resultSet.getInt("hours"));

    private final JdbcClient jdbc;

    public JdbcMaintenanceDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(MaintenanceWindow window) {
        jdbc.sql("""
                INSERT INTO maintenance_log (station_id, equipment, started_at, ended_at, hours)
                VALUES (:stationId, :equipment, :startedAt, :endedAt, :hours)
                ON CONFLICT (station_id, equipment, started_at) DO UPDATE
                    SET ended_at = EXCLUDED.ended_at,
                        hours = EXCLUDED.hours
                """)
                .param("stationId", window.stationId())
                .param("equipment", window.equipment().name())
                .param("startedAt", window.startedAt())
                .param("endedAt", window.endedAt(), Types.TIMESTAMP)
                .param("hours", window.hours())
                .update();
    }

    @Override
    public List<MaintenanceWindow> findByStationOrderedByStart(String stationId) {
        return jdbc.sql("""
                SELECT station_id, equipment, started_at, ended_at, hours
                FROM maintenance_log
                WHERE station_id = :stationId
                ORDER BY started_at, equipment
                """)
                .param("stationId", stationId)
                .query(ROW_MAPPER)
                .list();
    }
}
