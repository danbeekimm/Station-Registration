package com.exem.dust.common.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 원본 파일의 한 행. 숫자도 문자열로 들어오고 결측은 JSON null이라 모두 String으로 받는다. */
public record RawDustRow(
        @JsonProperty("날짜") String measuredAt,
        @JsonProperty("측정소명") String stationName,
        @JsonProperty("측정소코드") String stationCode,
        @JsonProperty("PM10") String pm10,
        @JsonProperty("PM2.5") String pm25) {
}
