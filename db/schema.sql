-- dustwatch 테이블 스키마. dust DB에 dust 롤로 실행한다.
--   psql -U dust -h localhost -d dust -f db/schema.sql
-- 중앙 서버도 기동할 때마다 이 파일을 그대로 실행한다. 생성 문장은 모두 IF NOT EXISTS라
-- 두 번째 기동부터는 아무것도 바꾸지 않는다. 테이블·데이터를 지우는 문장은 없고, 초기 버전의 중복 인덱스를
-- 교체하는 DROP INDEX IF EXISTS만 있다.
-- 시각 컬럼 규칙: 측정 시각(논리 시간)은 시간대 없는 TIMESTAMP, 서버가 찍는 시각은 TIMESTAMPTZ.

-- 측정소 마스터와 인증 정보. 측정소 목록의 유일한 출처다.
CREATE TABLE IF NOT EXISTS station (
    station_id     VARCHAR(10)  PRIMARY KEY,
    name           VARCHAR(50)  NOT NULL,
    token_hash     CHAR(64)     NOT NULL,                 -- SHA-256(station_id ':' token) 16진수. 평문 토큰은 저장하지 않는다
    registered_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 수신한 측정값 원본. pm10/pm25가 NULL이면 해당 장비 점검 시간이다.
CREATE TABLE IF NOT EXISTS measurement (
    id           BIGSERIAL    PRIMARY KEY,
    station_id   VARCHAR(10)  NOT NULL REFERENCES station (station_id),
    seq          INTEGER      NOT NULL,                   -- 측정소가 매긴 전송 순번. 재접속 시 재개 지점 계산용
    measured_at  TIMESTAMP    NOT NULL,                   -- 측정 시각(정시)
    pm10         NUMERIC(7,1),
    pm25         NUMERIC(7,1),
    o3           NUMERIC(6,3),                            -- 제공 데이터에 없어 항상 NULL. 오존 룰 입력 자리
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (station_id, measured_at)                      -- 재전송·재접속 중복 수신을 멱등 처리. 시각순 조회·최신 N건도 이 인덱스가 담당
);

-- 재접속 시 재개 지점(MAX(seq))을 단건으로 꺼내기 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_measurement_station_seq
    ON measurement (station_id, seq DESC);
DROP INDEX IF EXISTS idx_measurement_station_time;       -- 초기 버전의 중복 인덱스 정리(UNIQUE 인덱스와 같은 열)

-- 장비별 점검 구간. 연속 NULL 구간 하나가 1행이다.
CREATE TABLE IF NOT EXISTS maintenance_log (
    id          BIGSERIAL    PRIMARY KEY,
    station_id  VARCHAR(10)  NOT NULL REFERENCES station (station_id),
    equipment   VARCHAR(5)   NOT NULL CHECK (equipment IN ('PM10', 'PM25')),
    started_at  TIMESTAMP    NOT NULL,                    -- 첫 NULL 측정 시각
    ended_at    TIMESTAMP,                                -- 정상 측정이 재개된 시각. NULL이면 점검 진행 중
    hours       SMALLINT     NOT NULL DEFAULT 1,          -- NULL이 이어진 시간 수
    UNIQUE (station_id, equipment, started_at)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_station_open
    ON maintenance_log (station_id, ended_at);           -- 측정소별 진행 중 점검(대시보드 카드)
CREATE INDEX IF NOT EXISTS idx_maintenance_started
    ON maintenance_log (started_at DESC);                -- 최근 점검 피드

-- 시간별 경보 발령 기록. 한 측정소의 한 시각에는 최고 등급 1건만 남는다.
CREATE TABLE IF NOT EXISTS alert_log (
    id               BIGSERIAL     PRIMARY KEY,
    station_id       VARCHAR(10)   NOT NULL REFERENCES station (station_id),
    issued_at        TIMESTAMP     NOT NULL,              -- 발령 시각 = 기준을 충족한 측정 시각
    grade            SMALLINT      NOT NULL CHECK (grade BETWEEN 1 AND 5),
    level            VARCHAR(20)   NOT NULL,              -- 사람이 읽는 경보 단계명
    pollutant        VARCHAR(5)    NOT NULL CHECK (pollutant IN ('PM10', 'PM25', 'O3')),
    trigger_value    NUMERIC(8,3)  NOT NULL,              -- 발령 시각의 농도. O3 ppm도 손실 없이 저장
    sustained_hours  SMALLINT      NOT NULL,              -- 기준 이상이 이어진 시간 수
    slack_status     VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                                   CHECK (slack_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    slack_attempts   SMALLINT      NOT NULL DEFAULT 0,
    slack_next_at    TIMESTAMPTZ   NOT NULL DEFAULT now(), -- 다음 전송 시도 가능 시각(재시도 백오프, 429 Retry-After)
    slack_sent_at    TIMESTAMPTZ,
    slack_error      TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (station_id, issued_at)
);

CREATE INDEX IF NOT EXISTS idx_alert_issued
    ON alert_log (issued_at DESC);                       -- 최근 경보 피드
CREATE INDEX IF NOT EXISTS idx_alert_station_issued
    ON alert_log (station_id, issued_at DESC);           -- 측정소별 마지막 경보·건수
-- Slack outbox: PENDING 행만 담는 부분 인덱스. 디스패처가 issued_at 순 첫 행을 정렬 없이 꺼내고, 전송이 끝난 행은 인덱스에서 빠진다
CREATE INDEX IF NOT EXISTS idx_alert_slack_pending
    ON alert_log (issued_at, id) WHERE slack_status = 'PENDING';
DROP INDEX IF EXISTS idx_alert_slack_queue;              -- 초기 버전의 (slack_status, slack_next_at) 인덱스 정리

-- 인증·검증 거부 기록. 검증이 실제로 동작했다는 근거로 남긴다.
CREATE TABLE IF NOT EXISTS connection_reject_log (
    id           BIGSERIAL     PRIMARY KEY,
    rejected_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    remote_ip    VARCHAR(45)   NOT NULL,                  -- IPv6 표기까지 수용
    station_id   VARCHAR(10),                             -- 인증 전 거부는 NULL일 수 있다
    reason_code  VARCHAR(30)   NOT NULL,
    raw_message  VARCHAR(512)                             -- 원문 앞 512자
);

CREATE INDEX IF NOT EXISTS idx_reject_time
    ON connection_reject_log (rejected_at DESC);         -- 최근 거부 목록
CREATE INDEX IF NOT EXISTS idx_reject_reason
    ON connection_reject_log (reason_code);              -- 사유별 누적 집계(대시보드)
