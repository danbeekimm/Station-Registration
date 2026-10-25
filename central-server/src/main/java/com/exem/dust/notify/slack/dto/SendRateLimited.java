package com.exem.dust.notify.slack.dto;

import java.time.Duration;

/** 429. Slack이 알려준 시간만큼 미루고 시도 횟수에는 세지 않는다. */
public record SendRateLimited(Duration retryAfter) implements SendOutcome {
}
