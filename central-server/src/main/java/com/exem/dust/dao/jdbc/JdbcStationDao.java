package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.StationDao;
import com.exem.dust.domain.station.RegisteredStation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStationDao implements StationDao {

    private static final RowMapper<RegisteredStation> ROW_MAPPER = (resultSet, rowNumber) -> new RegisteredStation(
            resultSet.getString("station_id"),
            resultSet.getString("name"),
            resultSet.getString("token_hash"));

    private final JdbcClient jdbc;

    public JdbcStationDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** HELLO 인증 때마다 호출된다. 캐시를 두지 않아 등록 직후 바로 반영된다. */
    @Override
    public Optional<RegisteredStation> findById(String stationId) {
        return jdbc.sql("SELECT station_id, name, token_hash FROM station WHERE station_id = :stationId")
                .param("stationId", stationId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** 등록된 측정소 전체. 코드순. */
    @Override
    public List<RegisteredStation> findAll() {
        return jdbc.sql("SELECT station_id, name, token_hash FROM station ORDER BY station_id")
                .query(ROW_MAPPER)
                .list();
    }

    /** 있으면 이름·해시를 갱신하고 없으면 등록한다. registerStation이 쓴다. 초기 시드(db/seed.sql)는 DO NOTHING이라 기존 행을 바꾸지 않는다. */
    @Override
    public void upsert(RegisteredStation station) {
        jdbc.sql("""
                INSERT INTO station (station_id, name, token_hash)
                VALUES (:stationId, :name, :tokenHash)
                ON CONFLICT (station_id) DO UPDATE
                    SET name = EXCLUDED.name,
                        token_hash = EXCLUDED.token_hash,
                        updated_at = now()
                """)
                .param("stationId", station.id())
                .param("name", station.name())
                .param("tokenHash", station.tokenHash())
                .update();
    }
}
