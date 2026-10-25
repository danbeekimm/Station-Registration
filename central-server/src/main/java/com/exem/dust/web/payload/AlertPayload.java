package com.exem.dust.web.payload;

import java.time.LocalDateTime;

/** SSE alert 이벤트 본문. 피드 행과 카드 배지를 갱신한다. */
public record AlertPayload(
        String stationId,
        LocalDateTime issuedAt,
        int grade,
        String level,
        String pollutant,
        double triggerValue,
        int sustainedHours) {
}
