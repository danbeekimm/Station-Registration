package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.MaintenanceDao;
import com.exem.dust.domain.maintenance.Equipment;
import com.exem.dust.domain.maintenance.MaintenanceWindow;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMaintenanceDaoTest extends IntegrationTestSupport {

    @Autowired
    MaintenanceDao maintenanceDao;

    @Test
    void startExtendAndEndUpdateTheSameRow() {
        var started = MaintenanceWindow.startedAt("111311", Equipment.PM25, LocalDateTime.of(2023, 3, 25, 4, 0));

        maintenanceDao.upsert(started);
        maintenanceDao.upsert(started.extendedByOneHour());
        var ended = started.extendedByOneHour().extendedByOneHour().endedAt(LocalDateTime.of(2023, 3, 25, 7, 0));
        maintenanceDao.upsert(ended);

        assertThat(maintenanceDao.findByStationOrderedByStart("111311")).containsExactly(ended);
        assertThat(jdbc.sql("SELECT count(*) FROM maintenance_log").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void differentEquipmentAtSameHourAreSeparateRows() {
        var at = LocalDateTime.of(2023, 3, 2, 12, 0);

        maintenanceDao.upsert(MaintenanceWindow.startedAt("111121", Equipment.PM10, at));
        maintenanceDao.upsert(MaintenanceWindow.startedAt("111121", Equipment.PM25, at));

        assertThat(maintenanceDao.findByStationOrderedByStart("111121"))
                .extracting(MaintenanceWindow::equipment).containsExactly(Equipment.PM10, Equipment.PM25);
    }
}
