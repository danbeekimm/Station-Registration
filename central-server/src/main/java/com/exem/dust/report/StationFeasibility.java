package com.exem.dust.report;

import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.domain.measurement.Pollutant;
import com.exem.dust.report.dto.ConcentrationPeak;
import com.exem.dust.report.dto.RuleStats;

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
        Map<Pollutant, ConcentrationPeak> peaks,
        Map<AlertRule, Integer> recordedAlerts,
        Map<AlertRule, RuleStats> ruleStats,
        Map<Equipment, List<MaintenanceWindow>> maintenanceWindows,
        Map<String, Long> missingRepresentations,
        int timeGaps,
        List<String> parserWarnings) {

    /** alert_log에 남을 총 건수. 시뮬레이터 재생 후 DB 건수와 같아야 한다. */
    public int totalRecordedAlerts() {
        return recordedAlerts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
