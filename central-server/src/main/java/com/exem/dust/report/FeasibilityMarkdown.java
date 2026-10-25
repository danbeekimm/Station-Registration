package com.exem.dust.report;

import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.measurement.Pollutant;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** 분석 결과를 README에 그대로 붙일 수 있는 Markdown으로 만든다. */
public final class FeasibilityMarkdown {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("MM-dd HH:00");

    public String render(List<StationFeasibility> stations) {
        var out = new StringBuilder();
        out.append("### 요약: 등급별 실제 발령 가능 여부\n\n");
        out.append("| 등급 | 경보 단계 | 기준 |");
        stations.forEach(station -> out.append(' ').append(station.stationName()).append(" |"));
        out.append(" 결론 |\n|---|---|---|");
        stations.forEach(station -> out.append("---|"));
        out.append("---|\n");
        for (var rule : AlertRule.BY_SEVERITY) {
            out.append("| %d | %s | %s |".formatted(rule.grade(), rule.label(), describeRule(rule)));
            stations.forEach(station -> out.append(" %d건 |".formatted(station.recordedAlerts().get(rule))));
            out.append(' ').append(conclusion(rule, stations)).append(" |\n");
        }
        out.append("\n건수는 시각마다 최고 등급 1건만 기록하는 alert_log 기준이다. ")
                .append("상위 등급에 가려진 시간까지 포함한 룰별 조건 충족 시간은 아래 측정소별 표에 있다.\n");

        for (var station : stations) {
            renderStation(out, station);
        }
        return out.toString();
    }

    private void renderStation(StringBuilder out, StationFeasibility station) {
        out.append("\n### %s (%s) — %d행, 시각 누락 %d, 건너뛴 행 %d, 기록될 경보 %d건\n\n".formatted(
                station.stationName(), station.stationCode(), station.rowCount(), station.timeGaps(),
                station.parserWarnings().size(), station.totalRecordedAlerts()));

        out.append("| 룰 | 조건 충족 시간 | 기록 건수 | 최장 연속 ≥기준 | 최댓값 | 비고 |\n|---|---|---|---|---|---|\n");
        for (var rule : AlertRule.BY_SEVERITY) {
            var stats = station.ruleStats().get(rule);
            var peak = Optional.ofNullable(station.peaks().get(rule.pollutant()));
            out.append("| %d등급 %s | %d시간 | %d건 | %s | %s | %s |\n".formatted(
                    rule.grade(), rule.label(), stats.hoursMeetingRule(), station.recordedAlerts().get(rule),
                    describeStreak(stats.longestStreak()),
                    peak.map(found -> "%s (%s)".formatted(formatValue(found.value()), HOUR.format(found.at()))).orElse("데이터 없음"),
                    remark(rule, stats, peak)));
        }

        out.append("\n| 장비 | null 시간 | 점검 구간 | 진행 중 | 구간(시작 ~ 종료, 시간) |\n|---|---|---|---|---|\n");
        for (var equipment : Equipment.values()) {
            var windows = station.maintenanceWindows().get(equipment);
            int nullHours = windows.stream().mapToInt(window -> window.hours()).sum();
            long open = windows.stream().filter(window -> window.isOpen()).count();
            var described = windows.stream()
                    .map(window -> "%s ~ %s (%d)".formatted(HOUR.format(window.startedAt()),
                            window.endedAt() == null ? "진행 중" : HOUR.format(window.endedAt()), window.hours()))
                    .toList();
            out.append("| %s | %d | %d | %d | %s |\n".formatted(equipment, nullHours, windows.size(), open,
                    described.isEmpty() ? "-" : String.join(", ", described)));
        }
        out.append("\n결측 표현: ").append(station.missingRepresentations().isEmpty() ? "없음"
                : station.missingRepresentations().toString()).append('\n');
        if (!station.parserWarnings().isEmpty()) {
            out.append("\n파서 경고:\n");
            station.parserWarnings().forEach(warning -> out.append("- ").append(warning).append('\n'));
        }
    }

    private static String conclusion(AlertRule rule, List<StationFeasibility> stations) {
        if (rule.pollutant() == Pollutant.O3) {
            return "발령 불가 — 제공 데이터에 오존 측정값이 없음";
        }
        var feasible = stations.stream().filter(station -> station.recordedAlerts().get(rule) > 0)
                .map(station -> "%s %d건".formatted(station.stationName(), station.recordedAlerts().get(rule))).toList();
        if (!feasible.isEmpty()) {
            return "발령 가능 — " + String.join(", ", feasible);
        }
        var highestPeak = stations.stream()
                .map(station -> station.peaks().get(rule.pollutant()))
                .filter(peak -> peak != null)
                .max((left, right) -> Double.compare(left.value(), right.value()));
        return highestPeak.map(peak -> "발령 불가 — 최댓값 %s로 기준 %s 미달".formatted(formatValue(peak.value()), formatValue(rule.threshold())))
                .orElse("발령 불가 — 데이터 없음");
    }

    private static String remark(AlertRule rule, StationFeasibility.RuleStats stats, Optional<StationFeasibility.Peak> peak) {
        if (rule.pollutant() == Pollutant.O3) {
            return "O3 데이터 없음";
        }
        if (stats.hoursMeetingRule() > 0) {
            return "충족";
        }
        if (stats.longestStreak().exists()) {
            return "기준 이상 %d시간만 지속, %d시간 미달".formatted(stats.longestStreak().hours(), rule.sustainedHours());
        }
        return peak.map(found -> "최댓값이 기준 %s 미달".formatted(formatValue(rule.threshold()))).orElse("");
    }

    private static String describeRule(AlertRule rule) {
        return "%s ≥ %s %s %d시간 이상".formatted(rule.pollutant().label(), formatValue(rule.threshold()), rule.pollutant().unit(), rule.sustainedHours());
    }

    private static String describeStreak(StationFeasibility.Streak streak) {
        if (!streak.exists()) {
            return "없음";
        }
        return "%d시간 (%s ~ %s)".formatted(streak.hours(), HOUR.format(streak.start()), HOUR.format(streak.end()));
    }

    private static String formatValue(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    static String formatHour(LocalDateTime time) {
        return HOUR.format(time);
    }
}
