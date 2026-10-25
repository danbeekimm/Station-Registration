package com.exem.dust.notify.slack;

import com.exem.dust.dao.AlertDao;
import com.exem.dust.dao.SlackOutboxDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Webhook URL이 비어 있는 기본 설정에서는 HTTP 호출 없이 SKIPPED로만 기록된다. 평가자가 Slack 없이도 바로 실행할 수 있어야 한다. */
class SlackSkippedWithoutUrlTest extends IntegrationTestSupport {

    @Autowired
    SlackDispatcher dispatcher;
    @Autowired
    AlertDao alertDao;
    @Autowired
    SlackOutboxDao outbox;
    @MockitoBean
    SlackWebhookClient webhookClient;

    @Test
    void pendingAlertsBecomeSkippedWithoutAnyHttpCall() {
        alertDao.insertIfAbsent(new AlertIssued("111311", LocalDateTime.of(2023, 3, 5, 3, 0), AlertRule.PM25_WATCH, 80.0, 2));
        alertDao.insertIfAbsent(new AlertIssued("111121", LocalDateTime.of(2023, 3, 5, 12, 0), AlertRule.PM25_WATCH, 79.0, 2));

        assertThat(dispatcher.dispatchOne()).isFalse();

        assertThat(outbox.countByStatus("SKIPPED")).isEqualTo(2);
        assertThat(outbox.countByStatus("PENDING")).isZero();
        verify(webhookClient, never()).send(anyString(), anyString());
    }
}
