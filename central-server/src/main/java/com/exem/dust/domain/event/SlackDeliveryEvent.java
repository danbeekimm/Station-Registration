package com.exem.dust.domain.event;

/**
 * Slack 전송 시도 결과.
 *
 * @param status SENT, RETRY, RATE_LIMITED, FAILED 중 하나
 */
public record SlackDeliveryEvent(String stationId, String status) {
}
