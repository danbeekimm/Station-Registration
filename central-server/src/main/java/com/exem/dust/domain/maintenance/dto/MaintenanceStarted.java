package com.exem.dust.domain.maintenance.dto;

import com.exem.dust.domain.maintenance.MaintenanceWindow;

/** 첫 null 시각. 새 구간이 열렸다. */
public record MaintenanceStarted(MaintenanceWindow window) implements MaintenanceChange {
}
