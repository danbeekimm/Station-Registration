# 데이터베이스

## 테이블

| 테이블 | 역할 | 핵심 제약 |
|---|---|---|
| `station` | 측정소 마스터와 인증 해시. 측정소 목록의 단일 출처 | PK `station_id`, 평문 토큰 없음 |
| `measurement` | 수신한 측정값 원본. `pm10`/`pm25` NULL = 점검, `o3`는 오존 룰 입력 자리 | `UNIQUE (station_id, measured_at)` → 재전송 멱등 |
| `alert_log` | 시간별 경보 발령 기록 + Slack 전송 상태(outbox) | `UNIQUE (station_id, issued_at)` → 시각당 최고 등급 1건, `grade 1~5` |
| `maintenance_log` | 장비별 점검 구간(연속 null 병합) | `UNIQUE (station_id, equipment, started_at)` |
| `connection_reject_log` | 인증·검증 거부 기록 | — |

- 측정 시각(논리 시간)은 시간대 없는 `TIMESTAMP`, 서버가 찍는 시각(`received_at`, `slack_sent_at` 등)은 `TIMESTAMPTZ`다.
- enum은 `CREATE TYPE` 대신 `VARCHAR + CHECK`로 두어 변경·JDBC 매핑을 단순하게 했다.

## 스크립트와 자동 실행

- 스크립트 세 개: `db/init.sql`(롤·DB, 재실행 안전, compose가 최초 1회 자동 실행), `db/schema.sql`(테이블·인덱스, `IF NOT EXISTS`만 사용), `db/seed.sql`(초기 측정소 3곳, `ON CONFLICT DO NOTHING`).
- 서버는 기동마다 `schema.sql`을 실행해 테이블 5개를 확인하고 `seed.sql`로 없는 측정소만 등록한 뒤 수신을 시작한다. 두 번째 기동부터는 아무것도 바꾸지 않는다. 테이블·데이터를 지우는 문장은 없고, 초기 버전의 중복 인덱스를 교체하는 `DROP INDEX IF EXISTS`만 있다. 설정 파일에는 데이터를 두지 않는다.
- 롤·DB 생성은 앱 밖(compose의 initdb 마운트 또는 psql)에서 한다. 앱에 관리자 자격증명을 두지 않기 위해서다.
- PostgreSQL 18 이미지는 데이터 디렉터리가 `/var/lib/postgresql/18/docker`로 바뀌어 compose 볼륨을 `/var/lib/postgresql`에 마운트한다. 예전처럼 `/var/lib/postgresql/data`에 걸면 컨테이너 재생성 시 데이터가 사라진다.

## 인덱스와 쿼리

인덱스는 실제 DAO 쿼리에 맞췄다.

- `measurement`: UNIQUE(station_id, measured_at)가 시각순 조회·최신 N건·평온 시각 탐색을 맡고, `(station_id, seq DESC)`가 재접속 재개 지점(`MAX(seq)`) 단건 조회를 맡는다.
- `alert_log`: Slack outbox는 `(issued_at, id) WHERE slack_status = 'PENDING'` 부분 인덱스라 디스패처가 정렬 없이 첫 행을 꺼내고, 전송이 끝난 행은 인덱스에서 빠진다. 피드용 `issued_at DESC`, 측정소별 마지막 경보용 `(station_id, issued_at DESC)`.
- `maintenance_log`: 측정소별 진행 중 점검용 `(station_id, ended_at)`, 피드용 `started_at DESC`.
- `connection_reject_log`: 최근 목록용 `rejected_at DESC`, 사유별 집계용 `reason_code`.
- 상태 복원은 "마지막 평온 시각" 이후만 읽는다(README 3절). 대시보드 차트 점은 측정소별 LATERAL 한 쿼리로 읽는다.

## 점검 내역(maintenance_log) 기록 규칙

- 측정값 `null`은 해당 **장비**가 점검 중이라는 뜻으로 본다. PM10과 PM2.5는 장비가 다르므로 따로 추적한다(`equipment` 컬럼).
- **연속 null 구간 하나를 점검 1건**으로 저장한다. `started_at`은 첫 null 시각, `ended_at`은 정상 측정이 재개된 시각, `hours`는 null이 이어진 시간 수다. 예: 노원구 03-08 12·13시 PM10 null → `started_at 12:00, ended_at 14:00, hours 2`.
- 마지막 데이터가 null로 끝나면 `ended_at`이 NULL인 **진행 중** 상태로 남는다. 제공 데이터에는 해당 사례가 없다.
- 시각이 누락되면 진행 중이던 구간은 마지막 null 시각 + 1시간에 끝난 것으로 닫고, 누락 뒤 값이 null이면 새 구간을 시작한다.
- 시작·연장·종료는 모두 `(station_id, equipment, started_at)` 키의 같은 행 upsert다. 재접속·재기동 뒤 null이 이어져도 상태 복원 덕에 새 행이 생기지 않고 `hours`만 누적된다.
- 시간별 원본 null은 `measurement`에 그대로 남아 있으므로 "시각마다 1행"으로 보고 싶을 때는 `SELECT * FROM measurement WHERE pm10 IS NULL OR pm25 IS NULL`로 조회할 수 있다.
