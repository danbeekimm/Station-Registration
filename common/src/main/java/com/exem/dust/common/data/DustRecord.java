package com.exem.dust.common.data;

import java.time.LocalDateTime;

/** 원본 파일의 한 행. 농도 null은 장비 점검(결측)이다. */
public record DustRecord(LocalDateTime measuredAt, Double pm10, Double pm25) {
}
