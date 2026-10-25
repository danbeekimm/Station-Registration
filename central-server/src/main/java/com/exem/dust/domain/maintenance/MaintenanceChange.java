package com.exem.dust.domain.maintenance;

/** 측정값 1건이 점검 구간에 일으킨 변화. 저장은 어느 경우든 같은 행의 upsert지만 로그·대시보드는 종류를 구분한다. */
public sealed interface MaintenanceChange {

    MaintenanceWindow window();

    /** 첫 null 시각. 새 구간이 열렸다. */
    record Started(MaintenanceWindow window) implements MaintenanceChange {
    }

    /** 다음 시각도 null. hours가 1 늘었다. */
    record Extended(MaintenanceWindow window) implements MaintenanceChange {
    }

    /** 정상 측정이 재개됐다. ended_at이 확정됐다. */
    record Ended(MaintenanceWindow window) implements MaintenanceChange {
    }
}
