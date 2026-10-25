package com.exem.dust.dao;

import com.exem.dust.domain.station.RegisteredStation;

import java.util.List;
import java.util.Optional;

public interface StationDao {

    Optional<RegisteredStation> findById(String stationId);

    List<RegisteredStation> findAll();

    /** 있으면 이름·해시를 갱신하고 없으면 등록한다. */
    void upsert(RegisteredStation station);
}
