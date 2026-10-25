package com.exem.dust.common.data;

import java.util.List;

/** 측정소 하나의 시계열. 측정 시각 오름차순이고 같은 시각은 하나만 있다. */
public record StationData(String stationCode, String stationName, List<DustRecord> records) {
}
