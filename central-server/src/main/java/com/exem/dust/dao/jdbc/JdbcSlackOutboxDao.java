package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.SlackOutboxDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.alert.PendingAlert;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class JdbcSlackOutboxDao implements SlackOutboxDao {

    private static final int ERROR_TEXT_LIMIT = 500;

    private static final RowMapper<PendingAlert> ROW_MAPPER = (resultSet, rowNumber) -> new PendingAlert(
            resultSet.getLong("id"),
            resultSet.getString("station_name"),
            new AlertIssued(
                    resultSet.getString("station_id"),
                    resultSet.getObject("issued_at", LocalDateTime.class),
                    AlertRule.ofGrade(resultSet.getInt("grade")),
                    resultSet.getBigDecimal("trigger_value").doubleValue(),
                    resultSet.getInt("sustained_hours")),
            resultSet.getInt("slack_attempts"));

    private final JdbcClient jdbc;

    public JdbcSlackOutboxDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PendingAlert> nextPending(Instant now) {
        return jdbc.sql("""
                SELECT a.id, a.station_id, s.name AS station_name, a.issued_at, a.grade,
                       a.trigger_value, a.sustained_hours, a.slack_attempts
                FROM alert_log a
                JOIN station s ON s.station_id = a.station_id
                WHERE a.slack_status = 'PENDING' AND a.slack_next_at <= :now
                ORDER BY a.issued_at, a.id
                LIMIT 1
                """)
                .param("now", utc(now))
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public void markSent(long id, Instant sentAt) {
        jdbc.sql("""
                UPDATE alert_log
                SET slack_status = 'SENT', slack_sent_at = :sentAt, slack_error = NULL
                WHERE id = :id
                """)
                .param("id", id)
                .param("sentAt", utc(sentAt))
                .update();
    }

    @Override
    public void scheduleRetry(long id, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("""
                UPDATE alert_log
                SET slack_attempts = :attempts, slack_next_at = :nextAt, slack_error = :error
                WHERE id = :id
                """)
                .param("id", id)
                .param("attempts", attempts)
                .param("nextAt", utc(nextAttemptAt))
                .param("error", truncate(error))
                .update();
    }

    @Override
    public void defer(long id, Instant nextAttemptAt) {
        jdbc.sql("UPDATE alert_log SET slack_next_at = :nextAt WHERE id = :id")
                .param("id", id)
                .param("nextAt", utc(nextAttemptAt))
                .update();
    }

    @Override
    public void markFailed(long id, int attempts, String error) {
        jdbc.sql("""
                UPDATE alert_log
                SET slack_status = 'FAILED', slack_attempts = :attempts, slack_error = :error
                WHERE id = :id
                """)
                .param("id", id)
                .param("attempts", attempts)
                .param("error", truncate(error))
                .update();
    }

    @Override
    public int markAllPendingSkipped() {
        return jdbc.sql("UPDATE alert_log SET slack_status = 'SKIPPED' WHERE slack_status = 'PENDING'").update();
    }

    @Override
    public long countByStatus(String status) {
        return jdbc.sql("SELECT count(*) FROM alert_log WHERE slack_status = :status")
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= ERROR_TEXT_LIMIT) {
            return error;
        }
        return error.substring(0, ERROR_TEXT_LIMIT);
    }
}
