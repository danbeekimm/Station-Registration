package com.exem.dust.dao;

import com.exem.dust.notify.slack.dto.PendingAlert;

import java.time.Duration;
import java.util.Optional;

/**
 * alert_log의 Slack 전송 상태 컬럼을 outbox로 다룬다. 별도 큐 테이블이 없으므로 경보 기록과 전송 상태가 한 행에 있다.
 * 상태 전이: PENDING → SENT | FAILED, URL 미설정 시 PENDING → SKIPPED.
 * 전송 시각 비교와 다음 시도 시각 계산은 모두 DB 시계(now())로 한다. 앱과 DB의 시계가 어긋나도 판정이 흔들리지 않게 하기 위해서다.
 */
public interface SlackOutboxDao {

    /** 전송 시도 가능 시각(slack_next_at)이 지난 가장 오래된 PENDING 1건. */
    Optional<PendingAlert> nextPending();

    void markSent(long id);

    /** 실패했지만 재시도 여지가 남은 경우. 시도 횟수와 백오프 뒤의 다음 시도 시각, 마지막 오류를 기록한다. */
    void scheduleRetry(long id, int attempts, Duration backoff, String error);

    /** 429처럼 횟수에 세지 않고 미루기만 하는 경우. */
    void defer(long id, Duration retryAfter);

    void markFailed(long id, int attempts, String error);

    /** Webhook URL이 없을 때 쌓인 PENDING을 한 번에 SKIPPED로 바꾼다. 바뀐 행 수를 돌려준다. */
    int markAllPendingSkipped();

    long countByStatus(String status);
}
