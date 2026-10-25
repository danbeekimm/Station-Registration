package com.exem.dust.domain.maintenance;

import com.exem.dust.domain.measurement.Pollutant;

/** 점검 추적 대상 장비. 측정값이 null이면 그 장비가 점검 중인 것으로 본다. 장비가 다르므로 PM10과 PM2.5를 따로 추적한다. */
public enum Equipment {
    PM10(Pollutant.PM10),
    PM25(Pollutant.PM25);

    private final Pollutant pollutant;

    Equipment(Pollutant pollutant) {
        this.pollutant = pollutant;
    }

    public Pollutant pollutant() {
        return pollutant;
    }
}
