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
import java.util.Optional;

@Repository
public class JdbcMeasurementDao implements MeasurementDao {

    private static final String SELECT_COLUMNS = "SELECT station_id, seq, measured_at, pm10, pm25, o3 FROM measurement";

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

    /** 저장된 마지막 seq. (station_id, seq DESC) 인덱스로 단건 조회된다. 없으면 0이라 재개 지점은 항상 +1로 계산할 수 있다. */
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

    /** 전체 이력. 평온 시각이 없는 측정소(처음부터 계속 고농도·결측)의 상태 복원에만 쓴다. */
    @Override
    public List<Measurement> findAllOrderedByTime(String stationId) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE station_id = :stationId ORDER BY measured_at")
                .param("stationId", stationId)
                .query(ROW_MAPPER)
                .list();
    }

    /** 평온 시각 이후만 읽는 상태 복원 입력. UNIQUE(station_id, measured_at) 인덱스의 범위 스캔이다. */
    @Override
    public List<Measurement> findFromInclusive(String stationId, LocalDateTime from) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE station_id = :stationId AND measured_at >= :from ORDER BY measured_at")
                .param("stationId", stationId)
                .param("from", from)
                .query(ROW_MAPPER)
                .list();
    }

    /** 최신 시각부터 거꾸로 인덱스를 훑다가 조건에 맞는 첫 행에서 멈춘다. 대부분의 시각이 평온하므로 보통 몇 행 안에 끝난다. */
    @Override
    public Optional<LocalDateTime> latestCalmHour(String stationId, double pm10Below, double pm25Below, double o3Below) {
        return jdbc.sql("""
                SELECT measured_at FROM measurement
                WHERE station_id = :stationId
                  AND pm10 IS NOT NULL AND pm25 IS NOT NULL
                  AND pm10 < :pm10Below AND pm25 < :pm25Below
                  AND (o3 IS NULL OR o3 < :o3Below)
                ORDER BY measured_at DESC
                LIMIT 1
                """)
                .param("stationId", stationId)
                .param("pm10Below", pm10Below)
                .param("pm25Below", pm25Below)
                .param("o3Below", o3Below)
                .query(LocalDateTime.class)
                .optional();
    }
}
