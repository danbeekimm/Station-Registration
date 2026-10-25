package com.exem.dust.service;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.RegisteredStation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수신 1건 = 트랜잭션 1개. 측정값 저장과 이후 단계(경보·점검 기록)가 부분적으로만 남지 않게 한다. */
@Service
public class IngestService {

    private final MeasurementDao measurementDao;

    public IngestService(MeasurementDao measurementDao) {
        this.measurementDao = measurementDao;
    }

    @Transactional
    public IngestResult ingest(RegisteredStation station, Data data) {
        var measurement = new Measurement(station.id(), data.seq(), data.measuredAt(), data.pm10(), data.pm25(), data.o3());
        if (!measurementDao.insertIfAbsent(measurement)) {
            return new IngestResult.Duplicate(measurement);
        }
        return new IngestResult.Stored(measurement);
    }

    /** 재접속한 측정소가 이어서 보낼 seq. 서버에 저장된 마지막 seq 다음이다. */
    @Transactional(readOnly = true)
    public int nextExpectedSeq(String stationId) {
        return measurementDao.maxSeq(stationId) + 1;
    }
}
