package com.exem.dust.report;

import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.domain.measurement.Pollutant;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 측정소 하나의 데이터로 계산한 발령 가능성.
 *
 * @param recordedAlerts      실제로 alert_log에 남을 건수(시각마다 최고 등급 1건). 종단 실행 결과와 일치해야 한다
 * @param ruleStats           룰별 기준 충족 통계. 상위 등급에 가려 기록되지 않은 시간도 포함한다
 * @param maintenanceWindows  장비별 점검 구간(연속 null 병합)
 * @param missingRepresentations 원본 파일에서 결측이 어떤 표현으로 들어 있는지(null, 빈 문자열, "-")와 그 개수
 * @param timeGaps            직전 행 + 1시간이 아닌 행의 수(시각 누락)
 */
public record StationFeasibility(
        String stationCode,
        String stationName,
        int rowCount,
        Map<Pollutant, Peak> peaks,
        Map<AlertRule, Integer> recordedAlerts,
        Map<AlertRule, RuleStats> ruleStats,
        Map<Equipment, List<MaintenanceWindow>> maintenanceWindows,
        Map<String, Long> missingRepresentations,
        int timeGaps,
        List<String> parserWarnings) {

    /** 물질별 최댓값과 그 시각. */
    public record Peak(double value, LocalDateTime at) {
    }

    /**
     * @param hoursMeetingRule 기준 이상이 sustainedHours 이상 이어져 룰 조건이 충족된 시간 수
     * @param longestStreak    기준 이상이 연속된 가장 긴 구간. 조건 미달(예: 1시간)이어도 기록해 "근접 미달"을 보여 준다
     */
    public record RuleStats(int hoursMeetingRule, Streak longestStreak) {
    }

    /** 기준 이상이 연속된 구간. 시작·종료 시각 포함. */
    public record Streak(LocalDateTime start, LocalDateTime end, int hours) {

        public static final Streak NONE = new Streak(null, null, 0);

        /** 한 시간이라도 기준 이상이 있었는지. */
        public boolean exists() {
            return hours > 0;
        }
    }

    /** alert_log에 남을 총 건수. 시뮬레이터 재생 후 DB 건수와 같아야 한다. */
    public int totalRecordedAlerts() {
        return recordedAlerts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
