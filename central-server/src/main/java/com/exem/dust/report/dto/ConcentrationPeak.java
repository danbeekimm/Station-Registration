package com.exem.dust.report.dto;

import java.time.LocalDateTime;

/** 물질별 최댓값과 그 시각. */
public record ConcentrationPeak(double value, LocalDateTime at) {
}
