package com.exem.dust.notify.slack.dto;

/** 4xx·5xx·타임아웃·연결 실패. */
public record SendFailed(String error) implements SendOutcome {
}
