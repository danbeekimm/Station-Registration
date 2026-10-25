package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.SlackOutboxDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.notify.slack.dto.PendingAlert;
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

    /** 전송 가능 시각이 지난 PENDING 중 가장 오래된 1건. 메시지 본문용 측정소 이름을 함께 읽는다. */
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

    /** 성공 처리. 이전 시도의 오류 문구는 지운다. */
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

    /** 재시도 예약. 상태는 PENDING 그대로 두고 횟수·다음 시각·오류만 적는다. */
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

    /** 429처럼 횟수에 세지 않고 미루기만 한다. */
    @Override
    public void defer(long id, Instant nextAttemptAt) {
        jdbc.sql("UPDATE alert_log SET slack_next_at = :nextAt WHERE id = :id")
                .param("id", id)
                .param("nextAt", utc(nextAttemptAt))
                .update();
    }

    /** 재시도 한도를 넘어 포기한다. 경보 기록 자체는 그대로 남는다. */
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

    /** URL이 없을 때 쌓인 PENDING을 한 문장으로 정리한다. 건마다 1초씩 쓰지 않기 위해서다. */
    @Override
    public int markAllPendingSkipped() {
        return jdbc.sql("UPDATE alert_log SET slack_status = 'SKIPPED' WHERE slack_status = 'PENDING'").update();
    }

    /** 상태별 건수. 테스트와 대시보드 요약용. */
    @Override
    public long countByStatus(String status) {
        return jdbc.sql("SELECT count(*) FROM alert_log WHERE slack_status = :status")
                .param("status", status)
                .query(Long.class)
                .single();
    }

    /** TIMESTAMPTZ 파라미터로 넘길 UTC 표현. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** 응답 본문이 길어도 행이 커지지 않게 자른다. */
    private static String truncate(String error) {
        if (error == null || error.length() <= ERROR_TEXT_LIMIT) {
            return error;
        }
        return error.substring(0, ERROR_TEXT_LIMIT);
    }
}
