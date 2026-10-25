package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.measurement.Measurement;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcMeasurementDao implements MeasurementDao {

    private static final RowMapper<Measurement> ROW_MAPPER = (resultSet, rowNumber) -> new Measurement(
            resultSet.getString("station_id"),
            resultSet.getInt("seq"),
            resultSet.getObject("measured_at", LocalDateTime.class),
            nullableDouble(resultSet, "pm10"),
            nullableDouble(resultSet, "pm25"),
            nullableDouble(resultSet, "o3"));

    private final JdbcClient jdbc;

    public JdbcMeasurementDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** (station_id, measured_at) 충돌이면 DO NOTHING으로 0행. 재전송·재접속 중복을 예외 없이 걸러 낸다. */
    @Override
    public boolean insertIfAbsent(Measurement measurement) {
        int inserted = jdbc.sql("""
                INSERT INTO measurement (station_id, seq, measured_at, pm10, pm25, o3)
                VALUES (:stationId, :seq, :measuredAt, :pm10, :pm25, :o3)
                ON CONFLICT (station_id, measured_at) DO NOTHING
                """)
                .param("stationId", measurement.stationId())
                .param("seq", measurement.seq())
                .param("measuredAt", measurement.measuredAt())
                .param("pm10", measurement.pm10(), Types.NUMERIC)
                .param("pm25", measurement.pm25(), Types.NUMERIC)
                .param("o3", measurement.o3(), Types.NUMERIC)
                .update();
        return inserted == 1;
    }

    /** 저장된 마지막 seq. 없으면 0이라 재개 지점은 항상 +1로 계산할 수 있다. */
    @Override
    public int maxSeq(String stationId) {
        return jdbc.sql("SELECT COALESCE(MAX(seq), 0) FROM measurement WHERE station_id = :stationId")
                .param("stationId", stationId)
                .query(Integer.class)
                .single();
    }

    /** NUMERIC 컬럼은 PgJDBC가 Double로 직접 변환해 주지 않아 BigDecimal을 거친다. NULL은 그대로 NULL. */
    static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    /** 상태 복원(전체 재생)의 입력. 시각 오름차순이어야 지속 판정이 맞는다. */
    @Override
    public List<Measurement> findAllOrderedByTime(String stationId) {
        return jdbc.sql("""
                SELECT station_id, seq, measured_at, pm10, pm25, o3
                FROM measurement
                WHERE station_id = :stationId
                ORDER BY measured_at
                """)
                .param("stationId", stationId)
                .query(ROW_MAPPER)
                .list();
    }
}
