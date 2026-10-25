package com.exem.dust.domain.alert;

/**
 * Slack 전송을 기다리는 alert_log 행. 측정소 이름은 메시지 본문용으로 함께 읽는다.
 *
 * @param attempts 지금까지 실패한 전송 시도 횟수
 */
public record PendingAlert(long id, String stationName, AlertIssued alert, int attempts) {
}
