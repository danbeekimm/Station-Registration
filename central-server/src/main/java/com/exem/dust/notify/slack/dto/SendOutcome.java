package com.exem.dust.notify.slack.dto;

/** Webhook 호출 결과. 디스패처가 상태 전이를 결정하는 입력이다. */
public sealed interface SendOutcome permits SendSucceeded, SendRateLimited, SendFailed {
}
