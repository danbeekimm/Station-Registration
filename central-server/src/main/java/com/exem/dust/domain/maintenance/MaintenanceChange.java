package com.exem.dust.domain.maintenance;

/** 측정값 1건이 점검 구간에 일으킨 변화. 저장은 어느 경우든 같은 행의 upsert지만 로그·대시보드는 종류를 구분한다. */
public sealed interface MaintenanceChange {

    MaintenanceWindow window();

    record Started(MaintenanceWindow window) implements MaintenanceChange {
    }

    record Extended(MaintenanceWindow window) implements MaintenanceChange {
    }

    record Ended(MaintenanceWindow window) implements MaintenanceChange {
    }
}
