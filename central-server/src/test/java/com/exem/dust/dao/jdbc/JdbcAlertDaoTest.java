package com.exem.dust.dao.jdbc;

import com.exem.dust.dao.AlertDao;
import com.exem.dust.domain.alert.AlertIssued;
import com.exem.dust.domain.alert.AlertRule;
import com.exem.dust.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAlertDaoTest extends IntegrationTestSupport {

    @Autowired
    AlertDao alertDao;

    @Test
    void storesOneAlertPerStationAndHourWithPendingSlackStatus() {
        var issuedAt = LocalDateTime.of(2023, 3, 23, 22, 0);
        var alert = new AlertIssued("111311", issuedAt, AlertRule.PM10_WARNING, 313.0, 2);

        assertThat(alertDao.insertIfAbsent(alert)).isTrue();
        assertThat(alertDao.insertIfAbsent(new AlertIssued("111311", issuedAt, AlertRule.PM10_WATCH, 313.0, 3))).isFalse();

        Map<String, Object> row = jdbc.sql("SELECT grade, level, pollutant, trigger_value, sustained_hours, slack_status FROM alert_log")
                .query().singleRow();
        // PgJDBC는 SMALLINT를 Integer로 돌려준다
        assertThat(row).containsEntry("grade", 2)
                .containsEntry("level", "미세먼지 경보")
                .containsEntry("pollutant", "PM10")
                .containsEntry("sustained_hours", 2)
                .containsEntry("slack_status", "PENDING");
        assertThat(alertDao.findByStationOrderedByTime("111311")).containsExactly(alert);
    }
}
