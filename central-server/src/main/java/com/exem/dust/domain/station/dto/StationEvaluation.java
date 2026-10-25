package com.exem.dust.domain.station.dto;

import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.maintenance.dto.MaintenanceChange;

import java.util.List;
import java.util.Optional;

/** 측정값 1건을 받아들인 결과. 그 시각의 발령(있다면)과 장비별 점검 변화. */
public record StationEvaluation(Optional<AlertIssued> alert, List<MaintenanceChange> maintenanceChanges) {
}
