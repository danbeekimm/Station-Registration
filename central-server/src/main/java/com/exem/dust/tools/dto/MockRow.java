package com.exem.dust.tools.dto;

import java.time.LocalDateTime;

/** 생성된 한 행. 농도는 제공 데이터처럼 정수 문자열이고 결측은 null이다. */
public record MockRow(LocalDateTime measuredAt, String stationName, String stationCode, String pm10, String pm25) {
}
