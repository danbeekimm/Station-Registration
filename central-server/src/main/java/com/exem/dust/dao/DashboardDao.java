package com.exem.dust.dao;

import com.exem.dust.web.dto.AlertRow;
import com.exem.dust.web.dto.ChartPoint;
import com.exem.dust.web.dto.MaintenanceRow;
import com.exem.dust.web.dto.StationCard;

import java.util.List;
import java.util.Map;

/** 대시보드 최초 렌더링용 읽기 전용 조회. 화면 전용 DTO를 바로 돌려준다. */
public interface DashboardDao {

    /** 등록된 모든 측정소. 아직 접속하지 않은 측정소도 빈 카드로 나온다. */
    List<StationCard> stationCards(int chartPoints);

    List<ChartPoint> recentPoints(String stationId, int limit);

    List<AlertRow> recentAlerts(int limit);

    List<MaintenanceRow> recentMaintenance(int limit);

    /** 거부 사유별 누적 건수. 많은 순. */
    Map<String, Long> rejectCountsByReason();

    long pendingSlackCount();
}
