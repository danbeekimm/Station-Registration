package com.exem.dust.web.dto;

import java.time.LocalDateTime;

/** 차트의 한 점. 농도 null은 점검 시각이라 선이 끊긴다. */
public record ChartPoint(LocalDateTime at, Double pm10, Double pm25) {
}
