package com.exem.dust.report.dto;

/**
 * 룰별 기준 충족 통계.
 *
 * @param hoursMeetingRule 기준 이상이 sustainedHours 이상 이어져 룰 조건이 충족된 시간 수
 * @param longestStreak    기준 이상이 연속된 가장 긴 구간. 조건 미달(예: 1시간)이어도 기록해 "근접 미달"을 보여 준다
 */
public record RuleStats(int hoursMeetingRule, ThresholdStreak longestStreak) {
}
