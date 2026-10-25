package com.exem.dust.web.dto;

import java.time.LocalDateTime;

/** 경보 피드 한 줄. */
public record AlertRow(
        String stationId,
        String stationName,
        LocalDateTime issuedAt,
        int grade,
        String level,
        String pollutant,
        double triggerValue,
        int sustainedHours,
        String slackStatus) {
}
