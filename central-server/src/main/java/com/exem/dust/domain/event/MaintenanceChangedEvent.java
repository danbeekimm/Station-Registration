package com.exem.dust.domain.event;

import com.exem.dust.domain.maintenance.MaintenanceChange;

/** 점검 구간이 시작·연장·종료됐다. 커밋 후 전달되어 대시보드 스트림이 구독한다. */
public record MaintenanceChangedEvent(MaintenanceChange change) {
}
