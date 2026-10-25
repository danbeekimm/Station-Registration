package com.exem.dust.domain.maintenance.dto;

import com.exem.dust.domain.maintenance.MaintenanceWindow;

/** 다음 시각도 null. hours가 1 늘었다. */
public record MaintenanceExtended(MaintenanceWindow window) implements MaintenanceChange {
}
