package com.exem.dust.domain.measurement;

/** 경보 기준이 정의된 오염물질. 측정값에서 자기 농도를 꺼내는 방법을 안다. */
public enum Pollutant {
    PM10("㎍/㎥") {
        @Override
        public Double valueOf(Measurement measurement) {
            return measurement.pm10();
        }
    },
    PM25("㎍/㎥") {
        @Override
        public Double valueOf(Measurement measurement) {
            return measurement.pm25();
        }
    },
    O3("ppm") {
        @Override
        public Double valueOf(Measurement measurement) {
            return measurement.o3();
        }
    };

    private final String unit;

    Pollutant(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }

    /** 해당 시각의 농도. null이면 측정값이 없다(장비 점검 또는 미측정 물질). */
    public abstract Double valueOf(Measurement measurement);
}
