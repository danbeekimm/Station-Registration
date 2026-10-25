package com.exem.dust.report;

import com.exem.dust.common.data.DustRecordParser;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.report.dto.ConcentrationPeak;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 제공 데이터 기준 기대값. 이 숫자는 시뮬레이터 종단 실행 후 alert_log·maintenance_log 건수와 같아야 한다. */
class FeasibilityAnalyzerTest {

    private final FeasibilityAnalyzer analyzer = new FeasibilityAnalyzer(new DustRecordParser());

    @Test
    void nowonMatchesExpectedAlertAndMaintenanceCounts() {
        var result = analyzer.analyze(provided("노원구.json"));

        assertThat(result.stationCode()).isEqualTo("111311");
        assertThat(result.rowCount()).isEqualTo(744);
        assertThat(result.timeGaps()).isZero();
        assertThat(result.totalRecordedAlerts()).isEqualTo(32);
        assertThat(result.recordedAlerts()).containsEntry(AlertRule.PM10_WARNING, 1)
                .containsEntry(AlertRule.PM25_WATCH, 22)
                .containsEntry(AlertRule.PM10_WATCH, 9)
                .containsEntry(AlertRule.PM25_WARNING, 0)
                .containsEntry(AlertRule.O3_WATCH, 0);
        assertThat(result.maintenanceWindows().get(Equipment.PM10)).hasSize(1);
        assertThat(result.maintenanceWindows().get(Equipment.PM25)).hasSize(6);
        assertThat(result.peaks().get(com.exem.dust.domain.measurement.Pollutant.PM10))
                .isEqualTo(new ConcentrationPeak(313.0, LocalDateTime.of(2023, 3, 23, 22, 0)));
        assertThat(result.ruleStats().get(AlertRule.PM10_WARNING).longestStreak().hours()).isEqualTo(2);
        assertThat(result.ruleStats().get(AlertRule.PM25_WARNING).longestStreak().exists()).isFalse();
        assertThat(result.missingRepresentations()).isEqualTo(Map.of("JSON null", 11L));
    }

    @Test
    void junggguAndGangseoMatchExpectedTotals() {
        assertThat(analyzer.analyze(provided("중구.json")).totalRecordedAlerts()).isEqualTo(35);

        var gangseo = analyzer.analyze(provided("강서구.json"));
        assertThat(gangseo.totalRecordedAlerts()).isEqualTo(39);
        assertThat(gangseo.recordedAlerts()).containsEntry(AlertRule.PM10_WARNING, 2);
        assertThat(gangseo.ruleStats().get(AlertRule.PM10_WARNING).hoursMeetingRule()).isEqualTo(2);
        assertThat(gangseo.ruleStats().get(AlertRule.PM10_WATCH).hoursMeetingRule()).isGreaterThan(gangseo.recordedAlerts().get(AlertRule.PM10_WATCH));
    }

    @Test
    void markdownContainsSummaryAndConclusions() {
        var markdown = new FeasibilityMarkdown().render(List.of(analyzer.analyze(provided("노원구.json"))));

        assertThat(markdown)
                .contains("| 1 | 초미세먼지 경보 |").contains("발령 불가")
                .contains("| 2 | 미세먼지 경보 |").contains("발령 가능 — 노원구 1건")
                .contains("| 5 | 오존 주의보 |").contains("오존 측정값이 없음")
                .contains("### 노원구 (111311) — 744행");
    }

    private static Path provided(String fileName) {
        var path = Path.of("..", "data", fileName);
        assumeTrue(Files.exists(path), "제공 데이터 파일이 없는 환경: " + fileName);
        return path;
    }
}
