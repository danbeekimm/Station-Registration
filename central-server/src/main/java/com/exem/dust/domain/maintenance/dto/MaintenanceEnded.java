package com.exem.dust.domain.maintenance.dto;

import com.exem.dust.domain.maintenance.MaintenanceWindow;

/** 정상 측정이 재개됐다. ended_at이 확정됐다. */
public record MaintenanceEnded(MaintenanceWindow window) implements MaintenanceChange {
}
