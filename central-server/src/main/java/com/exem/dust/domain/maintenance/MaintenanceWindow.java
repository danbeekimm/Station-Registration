package com.exem.dust.domain.maintenance;

import java.time.LocalDateTime;

/**
 * 장비 하나의 연속 점검 구간. maintenance_log 한 행과 대응한다.
 *
 * @param startedAt 첫 null 측정 시각
 * @param endedAt   정상 측정이 재개된 시각. null이면 진행 중
 * @param hours     null이 이어진 시간 수
 */
public record MaintenanceWindow(String stationId, Equipment equipment, LocalDateTime startedAt, LocalDateTime endedAt, int hours) {

    public static MaintenanceWindow startedAt(String stationId, Equipment equipment, LocalDateTime measuredAt) {
        return new MaintenanceWindow(stationId, equipment, measuredAt, null, 1);
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    /** 마지막으로 null이 관측된 시각. */
    public LocalDateTime lastNullAt() {
        return startedAt.plusHours(hours - 1);
    }

    public MaintenanceWindow extendedByOneHour() {
        return new MaintenanceWindow(stationId, equipment, startedAt, null, hours + 1);
    }

    public MaintenanceWindow endedAt(LocalDateTime resumedAt) {
        return new MaintenanceWindow(stationId, equipment, startedAt, resumedAt, hours);
    }
}
