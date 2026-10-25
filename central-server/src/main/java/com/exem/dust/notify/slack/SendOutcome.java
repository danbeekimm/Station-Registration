package com.exem.dust.notify.slack;

import java.time.Duration;

/** Webhook 호출 결과. 디스패처가 상태 전이를 결정하는 입력이다. */
public sealed interface SendOutcome {

    /** 2xx 응답. */
    record Sent() implements SendOutcome {
    }

    /** 429. Slack이 알려준 시간만큼 미루고 시도 횟수에는 세지 않는다. */
    record RateLimited(Duration retryAfter) implements SendOutcome {
    }

    /** 4xx·5xx·타임아웃·연결 실패. */
    record Failed(String error) implements SendOutcome {
    }
}
