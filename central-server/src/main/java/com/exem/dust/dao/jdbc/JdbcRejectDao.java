package com.exem.dust.dao.jdbc;

import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.dao.RejectDao;
import com.exem.dust.domain.reject.ConnectionReject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class JdbcRejectDao implements RejectDao {

    private static final RowMapper<ConnectionReject> ROW_MAPPER = (resultSet, rowNumber) -> new ConnectionReject(
            resultSet.getObject("rejected_at", OffsetDateTime.class).toInstant(),
            resultSet.getString("remote_ip"),
            resultSet.getString("station_id"),
            RejectReason.valueOf(resultSet.getString("reason_code")),
            resultSet.getString("raw_message"));

    private final JdbcClient jdbc;

    public JdbcRejectDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 거부 1건 기록. 시각은 UTC로 넣고 TIMESTAMPTZ가 보존한다. */
    @Override
    public void insert(ConnectionReject reject) {
        jdbc.sql("""
                INSERT INTO connection_reject_log (rejected_at, remote_ip, station_id, reason_code, raw_message)
                VALUES (:rejectedAt, :remoteIp, :stationId, :reasonCode, :rawMessage)
                """)
                .param("rejectedAt", OffsetDateTime.ofInstant(reject.rejectedAt(), java.time.ZoneOffset.UTC))
                .param("remoteIp", reject.remoteIp())
                .param("stationId", reject.stationId(), Types.VARCHAR)
                .param("reasonCode", reject.reason().name())
                .param("rawMessage", reject.rawMessage(), Types.VARCHAR)
                .update();
    }

    /** 최신순 거부 목록. */
    @Override
    public List<ConnectionReject> findRecent(int limit) {
        return jdbc.sql("""
                SELECT rejected_at, remote_ip, station_id, reason_code, raw_message
                FROM connection_reject_log
                ORDER BY rejected_at DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }
}
