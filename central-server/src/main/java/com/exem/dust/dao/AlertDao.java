package com.exem.dust.dao;

import com.exem.dust.domain.alert.AlertIssued;

import java.util.List;

public interface AlertDao {

    /** (station_id, issued_at)이 이미 있으면 저장하지 않고 false. 같은 시각의 경보는 한 건만 남는다. */
    boolean insertIfAbsent(AlertIssued alert);

    List<AlertIssued> findByStationOrderedByTime(String stationId);
}
