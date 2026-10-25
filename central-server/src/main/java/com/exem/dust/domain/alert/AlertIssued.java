package com.exem.dust.domain.alert;

import java.time.LocalDateTime;

/**
 * 한 시각의 발령 1건. alert_log 한 행과 대응한다.
 *
 * @param issuedAt       발령 시각 = 기준을 충족한 측정 시각(논리 시간)
 * @param triggerValue   그 시각의 농도
 * @param sustainedHours 기준 이상이 이어진 시간 수. 발령이 이어지는 동안 2, 3, 4…로 늘어난다
 */
public record AlertIssued(String stationId, LocalDateTime issuedAt, AlertRule rule, double triggerValue, int sustainedHours) {
}
