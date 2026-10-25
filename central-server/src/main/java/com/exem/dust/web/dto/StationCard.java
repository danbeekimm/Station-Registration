package com.exem.dust.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대시보드 측정소 카드. 최초 렌더링은 DB에서 채우고 이후에는 SSE로 갱신된다.
 *
 * @param lastGrade       마지막 발령 등급. 경보 기록이 없으면 null
 * @param openMaintenance 진행 중 점검 장비
 * @param points          최근 측정값(시각순)
 */
public record StationCard(
        String id,
        String name,
        LocalDateTime latestAt,
        Double pm10,
        Double pm25,
        Integer lastGrade,
        String lastLevel,
        LocalDateTime lastAlertAt,
        List<String> openMaintenance,
        long alertCount,
        List<ChartPoint> points) {
}
