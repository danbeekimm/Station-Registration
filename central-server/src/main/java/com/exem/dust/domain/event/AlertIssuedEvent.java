package com.exem.dust.domain.event;

import com.exem.dust.domain.alert.AlertIssued;

/** 경보 1건이 alert_log에 기록됐다. 커밋 후 전달되어 Slack 디스패처를 깨운다. */
public record AlertIssuedEvent(AlertIssued alert) {
}
