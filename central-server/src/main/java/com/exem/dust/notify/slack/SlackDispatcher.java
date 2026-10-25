package com.exem.dust.notify.slack;

import com.exem.dust.config.DustProperties;
import com.exem.dust.config.dto.SlackProperties;
import com.exem.dust.dao.SlackOutboxDao;
import com.exem.dust.domain.alert.PendingAlert;
import com.exem.dust.domain.event.AlertIssuedEvent;
import com.exem.dust.domain.event.SlackDeliveryEvent;
import com.exem.dust.notify.slack.dto.SendFailed;
import com.exem.dust.notify.slack.dto.SendRateLimited;
import com.exem.dust.notify.slack.dto.SendSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * alert_log의 PENDING 행을 outbox로 삼아 Slack에 보내는 단일 스레드 디스패처.
 * <ul>
 *   <li>메모리 큐가 없다. 재기동해도 미전송분이 DB에 남아 첫 주기부터 이어서 보낸다.</li>
 *   <li>한 건 보내면 sendInterval(1초)만큼 쉬어 Incoming Webhook의 초당 1건 제한을 지킨다.</li>
 *   <li>큐가 비면 1초 대기하되, 경보가 커밋되면 이벤트로 즉시 깨어난다.</li>
 *   <li>Slack 장애는 이 스레드에만 영향을 준다. 수신·판정·기록 경로는 Slack을 기다리지 않는다.</li>
 * </ul>
 */
@Component
public class SlackDispatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SlackDispatcher.class);
    private static final Duration IDLE_POLL = Duration.ofSeconds(1);

    private final SlackProperties slack;
    private final SlackOutboxDao outbox;
    private final SlackWebhookClient client;
    private final SlackMessageFormatter formatter;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final LinkedBlockingQueue<Boolean> wakeSignals = new LinkedBlockingQueue<>();

    private Thread worker;
    private volatile boolean running;

    public SlackDispatcher(DustProperties properties, SlackOutboxDao outbox, SlackWebhookClient client,
                           SlackMessageFormatter formatter, Clock clock, ApplicationEventPublisher events) {
        this.slack = properties.slack();
        this.outbox = outbox;
        this.client = client;
        this.formatter = formatter;
        this.clock = clock;
        this.events = events;
    }

    /** 디스패처 스레드를 띄운다. URL이 없으면 경고만 남기고, autoDispatch가 꺼져 있으면(테스트) 스레드를 만들지 않는다. */
    @Override
    public void start() {
        if (!slack.isConfigured()) {
            log.warn("Slack Webhook URL이 설정되지 않았습니다. 경보는 DB에만 기록되고 slack_status는 SKIPPED로 남습니다");
        }
        if (!slack.autoDispatch()) {
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("slack-dispatcher").start(this::loop);
        log.info("Slack 디스패처 시작 ({})", slack);
    }

    /** 보낼 게 있으면 1건 보내고 전송 간격만큼 쉰다. 없으면 깨움 신호나 1초를 기다린다. */
    private void loop() {
        while (running) {
            try {
                if (dispatchOne()) {
                    Thread.sleep(slack.sendInterval());
                } else {
                    wakeSignals.poll(IDLE_POLL.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // DB 장애 등. 루프를 죽이지 않고 잠시 뒤 다시 시도한다
                log.error("Slack 디스패처 주기 실패", e);
                sleepQuietly(IDLE_POLL);
            }
        }
    }

    /** 경보 기록 트랜잭션이 커밋된 뒤에만 깨어난다. 롤백된 경보를 보내는 일이 없다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertIssued(AlertIssuedEvent event) {
        wakeSignals.offer(Boolean.TRUE);
    }

    /**
     * 대기 중인 경보 1건을 처리한다.
     *
     * @return 처리한 건이 있으면 true. 없으면 false
     */
    public boolean dispatchOne() {
        if (!slack.isConfigured()) {
            int skipped = outbox.markAllPendingSkipped();
            if (skipped > 0) {
                log.info("Slack URL 미설정으로 경보 {}건을 SKIPPED 처리", skipped);
            }
            return false;
        }
        var now = Instant.now(clock);
        var next = outbox.nextPending(now);
        if (next.isEmpty()) {
            return false;
        }
        deliver(next.get(), now);
        return true;
    }

    /** 1건을 보내고 결과대로 행 상태를 바꾼 뒤 지표용 이벤트를 발행한다. */
    private void deliver(PendingAlert pending, Instant now) {
        var alert = pending.alert();
        var status = switch (client.send(slack.webhookUrl(), formatter.format(pending))) {
            case SendSucceeded _ -> {
                outbox.markSent(pending.id(), now);
                log.info("Slack 전송 완료: [{}] {} {}", alert.stationId(), alert.rule().label(), alert.issuedAt());
                yield "SENT";
            }
            case SendRateLimited(var retryAfter) -> {
                outbox.defer(pending.id(), now.plus(retryAfter));
                log.warn("Slack 429, {}초 후 재시도: [{}] {}", retryAfter.toSeconds(), alert.stationId(), alert.issuedAt());
                yield "RATE_LIMITED";
            }
            case SendFailed(var error) -> {
                int attempts = pending.attempts() + 1;
                if (attempts >= slack.maxAttempts()) {
                    outbox.markFailed(pending.id(), attempts, error);
                    log.error("Slack 전송 포기({}회 실패): [{}] {} - {}", attempts, alert.stationId(), alert.issuedAt(), error);
                    yield "FAILED";
                }
                var backoff = Duration.ofSeconds(1L << (attempts - 1));  // 1초, 2초, 4초…
                outbox.scheduleRetry(pending.id(), attempts, now.plus(backoff), error);
                log.warn("Slack 전송 실패({}회), {}초 후 재시도: [{}] {} - {}", attempts, backoff.toSeconds(), alert.stationId(), alert.issuedAt(), error);
                yield "RETRY";
            }
        };
        events.publishEvent(new SlackDeliveryEvent(alert.stationId(), status));
    }

    /** 장애 후 잠시 쉰다. 인터럽트는 플래그만 복원해 루프가 종료 조건을 보게 한다. */
    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 스레드를 깨워 끝낸다. 처리 중이던 건의 상태는 DB에 있으므로 다음 기동이 이어받는다. */
    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
