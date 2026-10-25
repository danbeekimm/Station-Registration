package com.exem.dust.service;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.dao.AlertDao;
import com.exem.dust.dao.MaintenanceDao;
import com.exem.dust.dao.MeasurementDao;
import com.exem.dust.domain.event.AlertIssuedEvent;
import com.exem.dust.domain.event.MaintenanceChangedEvent;
import com.exem.dust.domain.event.MeasurementStoredEvent;
import com.exem.dust.domain.maintenance.dto.MaintenanceChange;
import com.exem.dust.domain.maintenance.dto.MaintenanceEnded;
import com.exem.dust.domain.maintenance.dto.MaintenanceExtended;
import com.exem.dust.domain.maintenance.dto.MaintenanceStarted;
import com.exem.dust.domain.measurement.Measurement;
import com.exem.dust.domain.station.Station;
import com.exem.dust.service.dto.IngestResult;
import com.exem.dust.service.dto.MeasurementDuplicate;
import com.exem.dust.service.dto.MeasurementStored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수신 1건 = 트랜잭션 1개. 측정값 저장과 판정 결과(경보·점검) 기록이 부분적으로만 남지 않게 한다.
 * 중복 수신이면 판정하지 않는다. 그 시각은 이미 상태에 반영되어 있기 때문이다.
 * Slack 전송은 여기서 하지 않는다. 경보 이벤트만 발행하고 디스패처가 커밋 후 outbox(alert_log)를 읽는다.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final MeasurementDao measurementDao;
    private final AlertDao alertDao;
    private final MaintenanceDao maintenanceDao;
    private final ApplicationEventPublisher events;

    public IngestService(MeasurementDao measurementDao, AlertDao alertDao, MaintenanceDao maintenanceDao,
                         ApplicationEventPublisher events) {
        this.measurementDao = measurementDao;
        this.alertDao = alertDao;
        this.maintenanceDao = maintenanceDao;
        this.events = events;
    }

    /**
     * @param state 접속 세션이 들고 있는 측정소 판정 상태. 이 호출 안에서 갱신된다.
     *              트랜잭션이 실패하면 상태와 DB가 어긋나므로 호출자는 접속을 끊어 재접속 시 상태를 다시 복원하게 한다
     */
    @Transactional
    public IngestResult ingest(Station state, Data data) {
        var measurement = new Measurement(state.id(), data.seq(), data.measuredAt(), data.pm10(), data.pm25(), data.o3());
        if (!measurementDao.insertIfAbsent(measurement)) {
            return new MeasurementDuplicate(measurement);
        }
        var evaluation = state.accept(measurement);
        evaluation.alert().ifPresent(alert -> {
            if (alertDao.insertIfAbsent(alert)) {
                log.info("[{}] 경보 발령 {} ({}등급) {} {}={} {}시간 지속", alert.stationId(), alert.rule().label(), alert.rule().grade(),
                        alert.issuedAt(), alert.rule().pollutant(), alert.triggerValue(), alert.sustainedHours());
                events.publishEvent(new AlertIssuedEvent(alert));
            }
        });
        for (var change : evaluation.maintenanceChanges()) {
            maintenanceDao.upsert(change.window());
            logMaintenance(change);
            events.publishEvent(new MaintenanceChangedEvent(change));
        }
        events.publishEvent(new MeasurementStoredEvent(measurement));
        return new MeasurementStored(measurement, evaluation);
    }

    /** 점검 시작·종료만 로그로 남긴다. 매 시간 연장은 소음이다. */
    private static void logMaintenance(MaintenanceChange change) {
        var window = change.window();
        switch (change) {
            case MaintenanceStarted _ ->
                    log.info("[{}] {} 장비 점검 시작 {}", window.stationId(), window.equipment(), window.startedAt());
            case MaintenanceEnded _ ->
                    log.info("[{}] {} 장비 점검 종료 {} ~ {} ({}시간)", window.stationId(), window.equipment(),
                            window.startedAt(), window.endedAt(), window.hours());
            case MaintenanceExtended _ -> {
                // 매 시간 연장은 로그로 남길 만큼 중요하지 않다
            }
        }
    }

    /** 재접속한 측정소가 이어서 보낼 seq. 서버에 저장된 마지막 seq 다음이다. */
    @Transactional(readOnly = true)
    public int nextExpectedSeq(String stationId) {
        return measurementDao.maxSeq(stationId) + 1;
    }
}
