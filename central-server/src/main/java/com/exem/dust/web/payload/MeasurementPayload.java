package com.exem.dust.web.payload;

import java.time.LocalDateTime;

/** SSE measurement 이벤트 본문. 카드의 최신 농도와 차트 점이 된다. */
public record MeasurementPayload(String stationId, LocalDateTime at, Double pm10, Double pm25) {
}
