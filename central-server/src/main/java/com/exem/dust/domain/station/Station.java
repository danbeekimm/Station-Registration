package com.exem.dust.domain.station;

import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceChange;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.domain.measurement.Measurement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 측정소 하나의 판정 상태. 룰별 연속 충족 시간과 장비별 진행 중 점검을 들고 있다.
 * <p>모든 시간 판단은 측정 시각(논리 시간)으로 한다. 직전 측정 시각 + 1시간이 아니면(시각 누락) 지속이 끊긴 것으로 본다.
 * DB·소켓 의존이 없어 순수 객체 테스트가 가능하고, 재접속·재기동 시에는 저장된 측정값을 {@link #accept}로 다시 흘려 복원한다.
 */
public final class Station {

    /** 측정값 1건을 받아들인 결과. */
    public record Evaluation(Optional<AlertIssued> alert, List<MaintenanceChange> maintenanceChanges) {
    }

    private final String id;
    private LocalDateTime lastMeasuredAt;
    private final Map<AlertRule, Integer> streaks = new EnumMap<>(AlertRule.class);
    private final Map<Equipment, MaintenanceWindow> openMaintenance = new EnumMap<>(Equipment.class);

    public Station(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Optional<LocalDateTime> lastMeasuredAt() {
        return Optional.ofNullable(lastMeasuredAt);
    }

    /** 측정값 1건을 반영해 이 시각의 발령과 점검 변화를 돌려준다. 호출할 때마다 내부 상태가 그 시각까지 전진한다. */
    public Evaluation accept(Measurement measurement) {
        if (!measurement.stationId().equals(id)) {
            throw new IllegalArgumentException("측정소 %s의 상태에 %s 측정값을 넣을 수 없습니다".formatted(id, measurement.stationId()));
        }
        var measuredAt = measurement.measuredAt();
        boolean continuous = lastMeasuredAt != null && measuredAt.equals(lastMeasuredAt.plusHours(1));
        if (!continuous) {
            // 첫 측정이거나 시각이 누락됐다. "연속 2시간"을 입증할 수 없으니 지속 카운터를 모두 버린다
            streaks.clear();
        }
        var maintenanceChanges = trackMaintenance(measurement, continuous);
        var alert = evaluateAlert(measurement);
        lastMeasuredAt = measuredAt;
        return new Evaluation(alert, maintenanceChanges);
    }

    /** 룰별 연속 카운터를 갱신한 뒤, 지속 시간을 채운 룰 중 가장 심각한 하나를 고른다. */
    private Optional<AlertIssued> evaluateAlert(Measurement measurement) {
        for (var rule : AlertRule.values()) {
            // null(점검)이나 기준 미달은 지속을 끊는다. 점검 직후 첫 측정이 기준을 넘어도 그 시각엔 1시간째다
            if (rule.isMetBy(rule.pollutant().valueOf(measurement))) {
                streaks.merge(rule, 1, Integer::sum);
            } else {
                streaks.remove(rule);
            }
        }
        return AlertRule.BY_SEVERITY.stream()
                .filter(rule -> streaks.getOrDefault(rule, 0) >= rule.sustainedHours())
                .findFirst()
                .map(rule -> new AlertIssued(id, measurement.measuredAt(), rule,
                        rule.pollutant().valueOf(measurement), streaks.get(rule)));
    }

    /** 장비별로 점검 구간을 시작·연장·종료한다. 시각이 끊겼으면 이전 구간을 먼저 닫는다. */
    private List<MaintenanceChange> trackMaintenance(Measurement measurement, boolean continuous) {
        var changes = new ArrayList<MaintenanceChange>();
        for (var equipment : Equipment.values()) {
            var open = openMaintenance.get(equipment);
            if (open != null && !continuous) {
                // 시각 누락: 진행 중이던 구간은 마지막 null 시각 + 1시간에 끝난 것으로 닫는다
                changes.add(new MaintenanceChange.Ended(open.endedAt(open.lastNullAt().plusHours(1))));
                openMaintenance.remove(equipment);
                open = null;
            }
            var concentration = equipment.pollutant().valueOf(measurement);
            if (concentration == null) {
                var window = open == null
                        ? MaintenanceWindow.startedAt(id, equipment, measurement.measuredAt())
                        : open.extendedByOneHour();
                openMaintenance.put(equipment, window);
                changes.add(open == null ? new MaintenanceChange.Started(window) : new MaintenanceChange.Extended(window));
            } else if (open != null) {
                openMaintenance.remove(equipment);
                changes.add(new MaintenanceChange.Ended(open.endedAt(measurement.measuredAt())));
            }
        }
        return List.copyOf(changes);
    }
}
