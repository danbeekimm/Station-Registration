package com.exem.dust.web.payload;

import java.time.LocalDateTime;

/** SSE maintenance 이벤트 본문. kind는 started·extended·ended. */
public record MaintenancePayload(
        String stationId,
        String equipment,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int hours,
        String kind) {
}
