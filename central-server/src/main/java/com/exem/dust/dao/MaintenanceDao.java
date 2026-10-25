package com.exem.dust.dao;

import com.exem.dust.domain.maintenance.MaintenanceWindow;

import java.util.List;

public interface MaintenanceDao {

    /**
     * (station_id, equipment, started_at)로 식별해 없으면 넣고 있으면 ended_at·hours를 갱신한다.
     * 시작·연장·종료가 모두 같은 행의 upsert라 재접속 후 이어지는 점검도 새 행을 만들지 않는다.
     */
    void upsert(MaintenanceWindow window);

    List<MaintenanceWindow> findByStationOrderedByStart(String stationId);
}
