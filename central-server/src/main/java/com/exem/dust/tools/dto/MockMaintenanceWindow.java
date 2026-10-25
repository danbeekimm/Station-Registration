package com.exem.dust.tools.dto;

import com.exem.dust.domain.maintenance.Equipment;

import java.time.LocalDateTime;
import java.util.Set;

/** 가상 데이터에 심을 점검(결측) 구간. 시작·종료 시각 포함. */
public record MockMaintenanceWindow(LocalDateTime start, LocalDateTime end, Set<Equipment> equipment) {

    /** 이 시각에 이 장비가 점검 중인가. */
    public boolean covers(LocalDateTime at, Equipment target) {
        return !at.isBefore(start) && !at.isAfter(end) && equipment.contains(target);
    }
}
