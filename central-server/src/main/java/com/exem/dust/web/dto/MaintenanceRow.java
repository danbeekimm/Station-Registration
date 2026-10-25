package com.exem.dust.web.dto;

import java.time.LocalDateTime;

/** 점검 피드 한 줄. endedAt이 null이면 진행 중이다. */
public record MaintenanceRow(
        String stationId,
        String stationName,
        String equipment,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int hours) {
}
