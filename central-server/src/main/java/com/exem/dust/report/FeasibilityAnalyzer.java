package com.exem.dust.report;

import com.exem.dust.common.data.DustRecordParser;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceChange;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.measurement.Pollutant;
import com.exem.dust.domain.station.Station;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 데이터 파일 하나를 서버와 같은 파서·룰 엔진({@link Station})으로 한 번 훑어 발령 가능성을 계산한다.
 * 손으로 세지 않고 코드로 계산하므로 README의 검토 결과가 재현 가능하고, 종단 실행의 alert_log 건수와 비교하는 기대값이 된다.
 */
public final class FeasibilityAnalyzer {

    private static final TypeReference<List<Map<String, Object>>> RAW_ROWS = new TypeReference<>() {
    };

    private final DustRecordParser parser;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public FeasibilityAnalyzer(DustRecordParser parser) {
        this.parser = parser;
    }

    /** 파일 하나를 처음부터 끝까지 재생해 측정소별 통계를 만든다. 측정소가 여럿 섞인 파일은 받지 않는다. */
    public StationFeasibility analyze(Path file) {
        var dustFile = parser.parse(file);
        var data = dustFile.single();
        var station = new Station(data.stationCode());

        var peaks = new EnumMap<Pollutant, StationFeasibility.Peak>(Pollutant.class);
        var recordedAlerts = new EnumMap<AlertRule, Integer>(AlertRule.class);
        var streaks = new EnumMap<AlertRule, Integer>(AlertRule.class);
        var hoursMeetingRule = new EnumMap<AlertRule, Integer>(AlertRule.class);
        var longestStreaks = new EnumMap<AlertRule, StationFeasibility.Streak>(AlertRule.class);
        var windows = new EnumMap<Equipment, List<MaintenanceWindow>>(Equipment.class);
        var openWindows = new EnumMap<Equipment, MaintenanceWindow>(Equipment.class);
        for (var rule : AlertRule.values()) {
            recordedAlerts.put(rule, 0);
            hoursMeetingRule.put(rule, 0);
            longestStreaks.put(rule, StationFeasibility.Streak.NONE);
        }
        for (var equipment : Equipment.values()) {
            windows.put(equipment, new ArrayList<>());
        }

        LocalDateTime previous = null;
        int timeGaps = 0;
        int seq = 0;
        for (var record : data.records()) {
            var measurement = new Measurement(data.stationCode(), ++seq, record.measuredAt(), record.pm10(), record.pm25(), null);
            boolean continuous = previous != null && record.measuredAt().equals(previous.plusHours(1));
            if (previous != null && !continuous) {
                timeGaps++;
            }

            var evaluation = station.accept(measurement);
            evaluation.alert().ifPresent(alert -> recordedAlerts.merge(alert.rule(), 1, Integer::sum));
            for (var change : evaluation.maintenanceChanges()) {
                var window = change.window();
                if (change instanceof MaintenanceChange.Ended) {
                    openWindows.remove(window.equipment());
                    windows.get(window.equipment()).add(window);
                } else {
                    openWindows.put(window.equipment(), window);
                }
            }

            for (var pollutant : Pollutant.values()) {
                var value = pollutant.valueOf(measurement);
                if (value != null && (!peaks.containsKey(pollutant) || value > peaks.get(pollutant).value())) {
                    peaks.put(pollutant, new StationFeasibility.Peak(value, record.measuredAt()));
                }
            }
            // Station과 같은 규칙으로 룰별 연속 시간을 따로 세어, 상위 등급에 가려진 충족 시간과 조건 미달 구간까지 본다
            for (var rule : AlertRule.values()) {
                int streak = rule.isMetBy(rule.pollutant().valueOf(measurement)) ? (continuous ? streaks.getOrDefault(rule, 0) : 0) + 1 : 0;
                streaks.put(rule, streak);
                if (streak >= rule.sustainedHours()) {
                    hoursMeetingRule.merge(rule, 1, Integer::sum);
                }
                if (streak > longestStreaks.get(rule).hours()) {
                    longestStreaks.put(rule, new StationFeasibility.Streak(record.measuredAt().minusHours(streak - 1), record.measuredAt(), streak));
                }
            }
            previous = record.measuredAt();
        }
        openWindows.forEach((equipment, window) -> windows.get(equipment).add(window));

        var ruleStats = new EnumMap<AlertRule, StationFeasibility.RuleStats>(AlertRule.class);
        for (var rule : AlertRule.values()) {
            ruleStats.put(rule, new StationFeasibility.RuleStats(hoursMeetingRule.get(rule), longestStreaks.get(rule)));
        }
        return new StationFeasibility(data.stationCode(), data.stationName(), data.records().size(), peaks, recordedAlerts,
                ruleStats, windows, countMissingRepresentations(file), timeGaps, dustFile.warnings());
    }

    /** 결측이 실제로 어떤 값으로 들어 있는지 원본을 다시 읽어 센다. 파서가 모두 null로 정규화하므로 여기서만 알 수 있다. */
    private Map<String, Long> countMissingRepresentations(Path file) {
        List<Map<String, Object>> rows;
        try {
            rows = mapper.readValue(Files.readAllBytes(file), RAW_ROWS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var counts = new TreeMap<String, Long>();
        for (var row : rows) {
            for (var key : List.of("PM10", "PM2.5")) {
                var value = row.get(key);
                String representation = switch (value) {
                    case null -> "JSON null";
                    case String text when text.isEmpty() -> "빈 문자열";
                    case String text when text.isBlank() -> "공백";
                    case String text when text.strip().equals("-") -> "\"-\"";
                    default -> null;
                };
                if (representation != null) {
                    counts.merge(representation, 1L, Long::sum);
                }
            }
        }
        return new LinkedHashMap<>(counts);
    }
}
