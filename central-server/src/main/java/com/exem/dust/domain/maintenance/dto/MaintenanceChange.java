package com.exem.dust.domain.maintenance.dto;

import com.exem.dust.domain.maintenance.MaintenanceWindow;

/** 측정값 1건이 점검 구간에 일으킨 변화. 저장은 어느 경우든 같은 행의 upsert지만 로그·대시보드는 종류를 구분한다. */
public sealed interface MaintenanceChange permits MaintenanceStarted, MaintenanceExtended, MaintenanceEnded {

    MaintenanceWindow window();
}
