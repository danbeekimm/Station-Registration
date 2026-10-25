package com.exem.dust.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DB가 필요한 통합 테스트의 공통 설정. Docker가 없는 환경에서는 통째로 건너뛰어 단위 테스트만으로도 빌드가 통과한다.
 * TCP 포트는 0으로 두어 임시 포트를 쓰고, Slack 디스패처 스레드는 띄우지 않으며(테스트가 직접 호출),
 * 테스트마다 수신 데이터 테이블을 비운다(측정소 시드는 유지).
 */
@SpringBootTest(properties = {"dust.tcp.port=0", "dust.slack.auto-dispatch=false"})
@Import(PostgresTestContainerConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanReceivedData() {
        jdbc.sql("DELETE FROM alert_log").update();
        jdbc.sql("DELETE FROM maintenance_log").update();
        jdbc.sql("DELETE FROM measurement").update();
        jdbc.sql("DELETE FROM connection_reject_log").update();
    }
}
