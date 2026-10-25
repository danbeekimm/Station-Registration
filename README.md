# dustwatch — 미세먼지 실시간 스트리밍 시뮬레이터 및 경보 시스템

한 달치(2023년 3월, 1시간 간격) 미세먼지(PM10)·초미세먼지(PM2.5) 시계열을 **1초 간격으로 재생**하는 측정소 시뮬레이터와,
이를 **TCP 소켓**으로 받아 경보를 판정·기록하고 **Slack**으로 알리는 중앙 서버를 Java로 구현했다.
측정값이 없는(null) 시각은 장비 점검으로 보아 점검 내역을 DB에 남긴다.

```
 측정소 시뮬레이터 (순수 Java, 파일당 프로세스 1개)            중앙 서버 (Spring Boot)
 ┌──────────────┐                                  ┌──────────────────────────────────────────┐
 │ 노원구.json   │─┐                                │ TCP 9000  ServerSocket + 접속당 가상 스레드  │
 │ 중구.json     │─┼─ NDJSON over TCP ─────────────▶│  HELLO 인증 → DATA 검증 → 저장 → 판정 → ACK │
 │ 강서구.json   │─┤   HELLO/WELCOME                │          │                                │
 │ 은평구.json*  │─┘   DATA/ACK/REJECT, BYE          │          ▼                                │
 └──────────────┘                                  │   PostgreSQL 18 (JDBC, JPA 미사용)          │
   * 미등록 측정소 등록 절차 데모용 가상 데이터            │   measurement · alert_log · maintenance_log │
                                                   │   connection_reject_log · station           │
                                                   │          │ alert_log PENDING = outbox          │
                                                   │          ▼                                │
                                                   │   SlackDispatcher ─▶ Slack Incoming Webhook │
                                                   └──────────────────────────────────────────┘
```

| 구분 | 선택 |
|---|---|
| 언어·런타임 | Java 25 (record, sealed interface, 패턴 매칭 switch, 가상 스레드) |
| 빌드 | Gradle 9.6 (래퍼 포함), 멀티모듈 `common` / `station-simulator` / `central-server` |
| 서버 | Spring Boot 4.1.2 — JdbcClient + DAO 인터페이스, RestClient. **JPA 미사용** |
| 통신 | `java.net.ServerSocket` / `Socket` 직접 사용, 개행 구분 JSON(NDJSON) |
| DB | PostgreSQL 18 (docker compose), 스키마는 `db/schema.sql` 제출 + 기동 시 자동 실행 |
| 알림 | Slack Incoming Webhook, URL은 설정·환경변수·실행 인자로 지정 |
| 대시보드·지표 | Thymeleaf 단일 페이지 + Server-Sent Events, Chart.js / Actuator health·prometheus |
| 테스트 | JUnit 5, AssertJ, Testcontainers(PostgreSQL 18), MockRestServiceServer — 105건 |

---

## 1. 실행 방법

### 사전 준비

- Docker (PostgreSQL 컨테이너와 통합 테스트용). 자체 PostgreSQL을 쓰려면 아래 "자체 PostgreSQL 사용" 참고
- JDK 17 이상 (Gradle 실행용). 컴파일·실행에 쓰는 **JDK 25는 Gradle 툴체인이 자동으로 내려받는다** (foojay resolver)
- Slack 알림을 보려면 Incoming Webhook URL (선택. 없어도 전체 흐름이 동작한다)

### 세 단계 실행

```bash
# 0. (선택) 포트가 겹치거나 Slack을 쓰려면 환경변수 파일을 만들어 값만 바꾼다. 없어도 기본값으로 실행된다
cp .env.example .env

# 1. PostgreSQL 18 기동 — 최초 1회 db/init.sql이 롤 dust와 DB dust를 만든다 (.env의 DUST_DB_PORT를 읽는다)
docker compose up -d

# 2. 중앙 서버 기동 — 기동마다 db/schema.sql(테이블 보장)과 db/seed.sql(측정소 3곳, 없는 것만 등록)을 실행한다
./run-server.sh                      # .env를 읽어 ./gradlew :central-server:bootRun 을 실행한다
#    Slack으로 보내려면:  SLACK_WEBHOOK_URL=https://hooks.slack.com/services/... ./run-server.sh

# 3. 다른 터미널에서 측정소 시뮬레이터 기동 — data/*.json 파일마다 프로세스 1개
./run-stations.sh          # 1초 간격 (원본 1시간 → 1초). 한 달치 744건에 약 12분 24초
./run-stations.sh 10       # 10ms 간격. 빠른 검증용, 약 8초
```

시뮬레이터 터미널에는 `[111311 노원구] seq=17 2023-03-01T16 pm10=34.0 pm25=24.0 → ACK` 형태로 한 줄씩 찍히고,
서버 로그에는 경보 발령·점검 시작/종료·Slack 전송 결과가 남는다.
`run-stations.sh`는 은평구(111191)가 서버에 등록되지 않은 측정소라 **의도적으로 거부**되는 것까지 보여 준다(5절).

### 결과 확인

브라우저로 **http://localhost:8080** 을 열면 측정소 카드(최신 농도·경보 등급·점검 중 장비·차트)와 경보·점검 피드가 1초마다 갱신된다(9절).
서버 상태는 `curl localhost:8080/actuator/health`로 본다.

```bash
# 측정소별 경보 건수(등급별)
docker compose exec postgres psql -U dust -d dust -c \
  "SELECT station_id, grade, level, count(*) FROM alert_log GROUP BY 1,2,3 ORDER BY 1,2"

# 점검 내역
docker compose exec postgres psql -U dust -d dust -c \
  "SELECT station_id, equipment, started_at, ended_at, hours FROM maintenance_log ORDER BY station_id, started_at"

# 거부 기록(인증·검증 실패)
docker compose exec postgres psql -U dust -d dust -c \
  "SELECT rejected_at, remote_ip, station_id, reason_code FROM connection_reject_log ORDER BY id"
```

10ms로 한 번 재생하면 노원구 32건, 중구 35건, 강서구 39건의 경보와 각각 7·4·3건의 점검 구간이 남아야 한다. 이 숫자는 4절 발령 가능성 검토의 계산 결과와 같다.

### 자체 PostgreSQL 사용

```bash
psql -U postgres -h <host> -f db/init.sql        # 롤 dust / DB dust 생성. 두 번 실행해도 안전
DUST_DB_URL=jdbc:postgresql://<host>:5432/dust DUST_DB_USER=dust DUST_DB_PASSWORD=dust ./gradlew :central-server:bootRun
```

테이블은 서버가 기동 시 `db/schema.sql`로 만든다. 직접 만들려면 `psql -U dust -d dust -f db/schema.sql`을 실행해도 된다(`IF NOT EXISTS`만 사용).

### 데이터를 지우고 처음부터

```bash
docker compose down -v && docker compose up -d     # 볼륨까지 삭제. 재기동(restart, down 없이 up)만으로는 데이터가 유지된다
```

---

## 2. 설정

모든 값은 `central-server/src/main/resources/application.yml`에 있고, **yml < 환경변수 < 실행 인자** 순으로 덮어쓴다.
환경변수 전체 목록과 기본값은 저장소 루트의 **`.env.example`** 에 있다. `cp .env.example .env`로 복사해 값을 바꾸면 `docker compose`, `run-server.sh`, `run-stations.sh`가 모두 그 파일을 읽는다(`.env`는 커밋되지 않는다).
평가 PC에서 5432·8080·9000이 이미 쓰이고 있으면 `.env`의 `DUST_DB_PORT`, `DUST_HTTP_PORT`, `DUST_TCP_PORT`만 바꾸면 된다. compose 포트 매핑, 서버 접속 URL, 대시보드 링크, 시뮬레이터 접속 주소가 함께 따라간다.

| 설정 | 기본값 | 환경변수 | 설명 |
|---|---|---|---|
| `dust.slack.webhook-url` | (비어 있음) | `SLACK_WEBHOOK_URL` | 비어 있으면 전송을 건너뛰고 `slack_status=SKIPPED`로 기록 |
| `dust.tcp.port` | 9000 | `DUST_TCP_PORT` | 측정소 수신 포트. 시뮬레이터 기본 주소도 이 값을 따른다 |
| `server.port` | 8080 | `DUST_HTTP_PORT` | 대시보드·SSE·Actuator |
| `dust.slack.dashboard-url` | `http://localhost:${DUST_HTTP_PORT}` | `DUST_DASHBOARD_URL` | Slack 메시지의 대시보드 링크. 비우면 생략 |
| PostgreSQL 호스트 포트 (compose) | 5432 | `DUST_DB_PORT` | 컨테이너 안은 항상 5432. 서버 접속 URL도 같은 변수를 쓴다 |
| `spring.datasource.*` | `localhost:5432/dust`, `dust/dust` | `DUST_DB_HOST`, `DUST_DB_PORT`, `DUST_DB_NAME`, `DUST_DB_USER`, `DUST_DB_PASSWORD` (또는 `DUST_DB_URL`) | `registerStation`도 같은 변수를 읽는다 |
| 측정소 초기 등록 | `db/seed.sql`의 3곳 | — | 기동 시 없는 측정소만 추가. 이후 추가·토큰 변경은 `registerStation`(DB) |
| 시뮬레이터 토큰 | 개발용 토큰 | `STATION_TOKEN_<측정소코드>` | `run-stations.sh`가 HELLO에 실어 보내는 값. 서버 쪽 토큰을 바꿨으면 같이 맞춘다 |
| `dust.validation.*` | 농도 0~2000, 오존 0~1ppm, 미래 1일, 순서 오류 3회 | | DATA 검증 한계 |
| `dust.tcp.hello-timeout` / `read-timeout` | 5s / 30s | | 접속 후 HELLO 대기, 메시지 간 최대 공백 |

실행 인자 예: `./gradlew :central-server:bootRun --args='--dust.slack.webhook-url=https://hooks.slack.com/services/...'`

**개발용 토큰은 의도적으로 저장소에 포함했다.** 평가자가 설정 없이 세 줄로 실행할 수 있어야 하기 때문이다.
`db/seed.sql`과 `run-stations.sh`의 기본값이 같은 토큰을 쓰며, DB에는 평문이 아닌 `SHA-256(측정소코드:토큰)` 해시만 저장된다(SQL의 `sha256()`과 Java `TokenHasher`가 같은 값을 내는 것을 테스트로 고정).
운영이라면 서버 쪽 토큰은 `registerStation`으로 바꾸고 시뮬레이터 쪽은 `STATION_TOKEN_<코드>` 환경변수로 맞추며, 실제 Webhook URL은 `.gitignore`에 포함된 `.env`나 환경변수에만 둔다.

---

## 3. 단계별 경보 처리 방식

### 발령 기준과 등급표

| 등급 | 경보 단계 | 물질 | 기준 | 지속 |
|---|---|---|---|---|
| 1 | 초미세먼지 경보 | PM2.5 | 150 ㎍/㎥ 이상 | 2시간 이상 |
| 2 | 미세먼지 경보 | PM10 | 300 ㎍/㎥ 이상 | 2시간 이상 |
| 3 | 초미세먼지 주의보 | PM2.5 | 75 ㎍/㎥ 이상 | 2시간 이상 |
| 4 | 미세먼지 주의보 | PM10 | 150 ㎍/㎥ 이상 | 2시간 이상 |
| 5 | 오존 주의보 | O3 | 0.12 ppm 이상 | 1시간 이상 |

다섯 룰은 `AlertRule` enum 한 곳에 선언되어 있고(`domain/alert/AlertRule.java`), 판정은 `Station.accept()` 한 메서드에서 끝난다.

### 판정 규칙

1. **시간 기준은 측정 시각(논리 시간)이다.** 1시간을 1초로 축소한 환경에서 서버 수신 시각으로 "2시간 지속"을 재면 의미가 없다. 수신 시각은 `received_at`에 기록만 한다.
2. **지속 판정:** 측정소마다 룰별 연속 충족 카운터를 둔다. 해당 시각 농도가 기준 **이상(≥)** 이면 +1, 미달이면 0으로 리셋한다. 카운터가 룰의 지속 시간(2시간, 오존은 1시간) 이상이 되는 시각부터 발령한다. 즉 기준을 2시간 연속 넘긴 **두 번째 시각**에 첫 발령이 난다.
3. **매 시각 기록:** 기준 충족이 이어지는 동안 시각마다 1건씩 기록한다(`sustained_hours`가 2, 3, 4…로 증가). 경보 해제 개념은 요구사항에 없어 만들지 않았다.
4. **최고 등급만:** 한 시각에 여러 룰이 충족되면 등급 숫자가 가장 작은 룰 하나만 발령·기록한다(1 > 2 > 3 > 4 > 5). `alert_log`의 `UNIQUE (station_id, issued_at)`이 이를 DB 수준에서도 보장한다.
5. **null(점검)은 지속을 끊는다.** 점검 중에는 농도가 없어 지속을 입증할 수 없으므로 카운터를 0으로 만든다. 점검 직후 첫 측정이 기준을 넘어도 그 시각엔 발령하지 않고 다음 시각에도 넘어야 발령한다. PM10 장비만 점검 중이면 PM2.5 룰은 영향을 받지 않는다.
6. **시각 누락도 지속을 끊는다.** 직전 측정 시각 + 1시간이 아니면 "연속 2시간" 정의가 깨지므로 모든 카운터를 리셋한다. 제공 데이터에는 누락이 없지만 규칙으로 두었다.
7. **중복 수신은 판정하지 않는다.** 재전송·재접속으로 같은 시각이 다시 오면 `measurement`의 UNIQUE 충돌로 저장을 건너뛰고 ACK만 다시 보낸다. 상태에는 이미 반영된 시각이기 때문이다.

### 예시: 노원구 2023-03-23 20시 ~ 03-24 06시 (실제 데이터)

| 시각 | PM10 | ≥150 연속 | ≥300 연속 | 기록 |
|---|---|---|---|---|
| 20시 | 230 | 1 | 0 | 없음 (1시간째) |
| 21시 | 307 | 2 | 1 | **4등급 미세먼지 주의보** (2시간 지속) |
| 22시 | 313 | 3 | 2 | **2등급 미세먼지 경보** — 4등급도 충족(3시간)이지만 상위 등급 1건만 |
| 23시 | 282 | 4 | 0 | 4등급 (4시간) |
| 00~05시 | 236 → 158 | 5~10 | 0 | 4등급, `sustained_hours` 5~10 |
| 06시 | 149 | 0 | 0 | 없음 |

### 상태 복원

판정 상태(카운터, 직전 시각, 진행 중 점검)는 접속 세션의 `Station` 객체에 있다. 측정소가 재접속하거나 서버가 재기동되면
HELLO 인증 직후 그 측정소의 저장된 측정값 전체(최대 744행)를 시각순으로 다시 `accept()`해 상태를 정확히 복원한다.
그래서 재접속 뒤 이어진 시각의 `sustained_hours`가 끊기지 않고, 진행 중이던 점검도 새 행 없이 같은 행에 누적된다.

---

## 4. 발령 가능성 검토 (제공 데이터 기준)

손으로 세지 않고 `./gradlew -q feasibility`가 서버와 **같은 파서·룰 엔진**으로 데이터 파일을 훑어 아래 표를 생성한다.
"기록 건수"는 시각마다 최고 등급 1건만 남기는 `alert_log` 기준이라, 시뮬레이터로 재생한 뒤의 DB 건수와 같아야 하며 실제로 같음을 확인했다.
**은평구는 제공 데이터가 아닌 가상 데이터**(5절)이므로 발령 가능성 결론은 노원구·중구·강서구 세 측정소로 판단한다.

### 결론

| 등급 | 경보 | 제공 데이터에서 실제 발령 가능? | 근거 |
|---|---|---|---|
| 1 | 초미세먼지 경보 | **불가** | PM2.5 ≥150이 한 시간도 없음. 최댓값 강서구 121(03-19 23시), 중구 114, 노원구 111 |
| 2 | 미세먼지 경보 | **가능** — 강서구 2건, 노원구 1건 | 강서구 03-23 19~21시 375·382·325로 3시간 연속 ≥300 → 20·21시 발령. 노원구 03-23 21~22시 307·313 → 22시 발령. 중구는 최댓값 289로 미달 |
| 3 | 초미세먼지 주의보 | **가능** — 73건 | 03-10~11, 03-19~20에 세 측정소 모두 장시간 ≥75. 중구는 16시간 연속 |
| 4 | 미세먼지 주의보 | **가능** — 30건 | 03-23~24 고농도 사건. 2등급이 발령된 시각은 4등급 조건도 충족하지만 상위 등급으로 기록 |
| 5 | 오존 주의보 | **불가** | 제공 데이터에 오존 측정값이 없다. 룰은 구현되어 있고(`O3_WATCH`, 1시간 지속) DATA 메시지에 `o3` 필드도 있어 값이 오면 동작한다 |

### `./gradlew -q feasibility` 출력

### 요약: 등급별 실제 발령 가능 여부

| 등급 | 경보 단계 | 기준 | 강서구 | 노원구 | 은평구* | 중구 | 결론 |
|---|---|---|---|---|---|---|---|
| 1 | 초미세먼지 경보 | PM2.5 ≥ 150 ㎍/㎥ 2시간 이상 | 0건 | 0건 | 0건 | 0건 | 발령 불가 — 최댓값 123로 기준 150 미달 |
| 2 | 미세먼지 경보 | PM10 ≥ 300 ㎍/㎥ 2시간 이상 | 2건 | 1건 | 2건 | 0건 | 발령 가능 — 강서구 2건, 노원구 1건, 은평구 2건 |
| 3 | 초미세먼지 주의보 | PM2.5 ≥ 75 ㎍/㎥ 2시간 이상 | 23건 | 22건 | 23건 | 28건 | 발령 가능 — 강서구 23건, 노원구 22건, 은평구 23건, 중구 28건 |
| 4 | 미세먼지 주의보 | PM10 ≥ 150 ㎍/㎥ 2시간 이상 | 14건 | 9건 | 8건 | 7건 | 발령 가능 — 강서구 14건, 노원구 9건, 은평구 8건, 중구 7건 |
| 5 | 오존 주의보 | O3 ≥ 0.12 ppm 1시간 이상 | 0건 | 0건 | 0건 | 0건 | 발령 불가 — 제공 데이터에 오존 측정값이 없음 |

\* 은평구는 가상 데이터. 1등급 결론의 최댓값 123도 은평구 값이며, 제공 데이터만의 최댓값은 강서구 121이다.

### 강서구 (111212) — 744행, 시각 누락 0, 건너뛴 행 0, 기록될 경보 39건

| 룰 | 조건 충족 시간 | 기록 건수 | 최장 연속 ≥기준 | 최댓값 | 비고 |
|---|---|---|---|---|---|
| 1등급 초미세먼지 경보 | 0시간 | 0건 | 없음 | 121 (03-19 23:00) | 최댓값이 기준 150 미달 |
| 2등급 미세먼지 경보 | 2시간 | 2건 | 3시간 (03-23 19:00 ~ 03-23 21:00) | 382 (03-23 20:00) | 충족 |
| 3등급 초미세먼지 주의보 | 23시간 | 23건 | 11시간 (03-10 23:00 ~ 03-11 09:00) | 121 (03-19 23:00) | 충족 |
| 4등급 미세먼지 주의보 | 20시간 | 14건 | 11시간 (03-23 17:00 ~ 03-24 03:00) | 382 (03-23 20:00) | 충족 |
| 5등급 오존 주의보 | 0시간 | 0건 | 없음 | 데이터 없음 | O3 데이터 없음 |

| 장비 | null 시간 | 점검 구간 | 진행 중 | 구간(시작 ~ 종료, 시간) |
|---|---|---|---|---|
| PM10 | 3 | 1 | 0 | 03-14 16:00 ~ 03-14 19:00 (3) |
| PM25 | 5 | 2 | 0 | 03-14 16:00 ~ 03-14 19:00 (3), 03-29 21:00 ~ 03-29 23:00 (2) |

결측 표현: {JSON null=8}

### 노원구 (111311) — 744행, 시각 누락 0, 건너뛴 행 0, 기록될 경보 32건

| 룰 | 조건 충족 시간 | 기록 건수 | 최장 연속 ≥기준 | 최댓값 | 비고 |
|---|---|---|---|---|---|
| 1등급 초미세먼지 경보 | 0시간 | 0건 | 없음 | 111 (03-19 23:00) | 최댓값이 기준 150 미달 |
| 2등급 미세먼지 경보 | 1시간 | 1건 | 2시간 (03-23 21:00 ~ 03-23 22:00) | 313 (03-23 22:00) | 충족 |
| 3등급 초미세먼지 주의보 | 22시간 | 22건 | 15시간 (03-19 21:00 ~ 03-20 11:00) | 111 (03-19 23:00) | 충족 |
| 4등급 미세먼지 주의보 | 10시간 | 9건 | 10시간 (03-23 20:00 ~ 03-24 05:00) | 313 (03-23 22:00) | 충족 |
| 5등급 오존 주의보 | 0시간 | 0건 | 없음 | 데이터 없음 | O3 데이터 없음 |

| 장비 | null 시간 | 점검 구간 | 진행 중 | 구간(시작 ~ 종료, 시간) |
|---|---|---|---|---|
| PM10 | 2 | 1 | 0 | 03-08 12:00 ~ 03-08 14:00 (2) |
| PM25 | 9 | 6 | 0 | 03-01 12:00 ~ 03-01 13:00 (1), 03-02 19:00 ~ 03-02 20:00 (1), 03-25 04:00 ~ 03-25 08:00 (4), 03-26 06:00 ~ 03-26 07:00 (1), 03-27 10:00 ~ 03-27 11:00 (1), 03-28 01:00 ~ 03-28 02:00 (1) |

결측 표현: {JSON null=11}

### 중구 (111121) — 744행, 시각 누락 0, 건너뛴 행 0, 기록될 경보 35건

| 룰 | 조건 충족 시간 | 기록 건수 | 최장 연속 ≥기준 | 최댓값 | 비고 |
|---|---|---|---|---|---|
| 1등급 초미세먼지 경보 | 0시간 | 0건 | 없음 | 114 (03-20 02:00) | 최댓값이 기준 150 미달 |
| 2등급 미세먼지 경보 | 0시간 | 0건 | 없음 | 289 (03-23 21:00) | 최댓값이 기준 300 미달 |
| 3등급 초미세먼지 주의보 | 28시간 | 28건 | 16시간 (03-19 20:00 ~ 03-20 11:00) | 114 (03-20 02:00) | 충족 |
| 4등급 미세먼지 주의보 | 7시간 | 7건 | 8시간 (03-23 18:00 ~ 03-24 01:00) | 289 (03-23 21:00) | 충족 |
| 5등급 오존 주의보 | 0시간 | 0건 | 없음 | 데이터 없음 | O3 데이터 없음 |

| 장비 | null 시간 | 점검 구간 | 진행 중 | 구간(시작 ~ 종료, 시간) |
|---|---|---|---|---|
| PM10 | 7 | 2 | 0 | 03-02 12:00 ~ 03-02 17:00 (5), 03-03 11:00 ~ 03-03 13:00 (2) |
| PM25 | 7 | 2 | 0 | 03-02 12:00 ~ 03-02 17:00 (5), 03-03 11:00 ~ 03-03 13:00 (2) |

결측 표현: {JSON null=14}

### 은평구 (111191, 가상 데이터) — 744행, 기록될 경보 33건

2등급 2건(03-23 21~22시, 최댓값 319), 3등급 23건, 4등급 8건. 점검 PM10 1구간(2시간), PM2.5 2구간(5시간). 전체 표는 `./gradlew -q feasibility`로 볼 수 있다.

### 데이터 특성 메모

- 세 파일 모두 744행(3/1 00시 ~ 3/31 23시), 시각 누락·중복 없음, `24`시 표기 없음.
- 결측은 전부 JSON `null`이다. 파서는 빈 문자열·공백·`"-"`도 결측으로 받지만 실제 파일에는 나오지 않았다.
- 중구와 강서구의 점검은 PM10·PM2.5가 같은 시각에 함께 비어 있고(두 장비 동시 점검), 노원구는 PM2.5만 비는 1시간 점검이 잦다.

---

## 5. 점검 내역 처리

- 측정값 `null`은 해당 **장비**가 점검 중이라는 뜻으로 본다. PM10과 PM2.5는 장비가 다르므로 따로 추적한다(`equipment` 컬럼).
- **연속 null 구간 하나를 점검 1건**으로 저장한다. `started_at`은 첫 null 시각, `ended_at`은 정상 측정이 재개된 시각, `hours`는 null이 이어진 시간 수다. 예: 노원구 03-08 12·13시 PM10 null → `started_at 12:00, ended_at 14:00, hours 2`.
- 마지막 데이터가 null로 끝나면 `ended_at`이 NULL인 **진행 중** 상태로 남는다. 제공 데이터에는 해당 사례가 없다.
- 시각이 누락되면 진행 중이던 구간은 마지막 null 시각 + 1시간에 끝난 것으로 닫고, 누락 뒤 값이 null이면 새 구간을 시작한다.
- 시작·연장·종료는 모두 `(station_id, equipment, started_at)` 키의 같은 행 upsert다. 재접속·재기동 뒤 null이 이어져도 상태 복원(3절) 덕에 새 행이 생기지 않고 `hours`만 누적된다.
- 시간별 원본 null은 `measurement`에 그대로 남아 있으므로 "시각마다 1행"으로 보고 싶을 때는 `SELECT * FROM measurement WHERE pm10 IS NULL OR pm25 IS NULL`로 조회할 수 있다.

---

## 6. 접속 인증, 메시지 검증, 측정소 추가

### HELLO 핸드셰이크

측정소는 접속 후 첫 메시지로 `HELLO`(측정소 코드·이름·토큰·프로토콜 버전·마지막 ACK seq)를 보낸다. 서버는 `station` 테이블의 `token_hash`와 **상수 시간 비교**하고,
성공이면 `WELCOME(resumeFromSeq)`로 이어서 보낼 seq를 알려 준다. 접속 후 5초 안에 HELLO가 없거나 HELLO가 아닌 메시지가 먼저 오면 거부 후 소켓을 닫는다.
같은 측정소의 새 접속이 인증을 통과하면 기존 접속을 닫는다(최신 접속 우선). 측정소가 비정상 종료해도 읽기 타임아웃 30초를 기다리지 않고 바로 재접속할 수 있다.

측정소 자동 등록은 하지 않는다. 누구나 소켓만 열면 가짜 측정값을 밀어 넣을 수 있는 구조는 경보 시스템으로서 결함이기 때문이다. `station` 테이블이 측정소 목록과 해시의 유일한 출처고, 서버는 HELLO마다 DB를 읽기 때문에 캐시가 없고 등록 직후 바로 접속된다.

### 거부 사유 코드

| 사유 | 시점 | 접속 | 의미 |
|---|---|---|---|
| `HELLO_EXPECTED` | 인증 전 | 종료 | 첫 메시지가 HELLO가 아님(형식 오류 포함) |
| `HELLO_TIMEOUT` | 인증 전 | 종료 | 5초 안에 HELLO가 오지 않음 |
| `UNSUPPORTED_PROTOCOL` | 인증 전 | 종료 | 프로토콜 버전 불일치 |
| `UNKNOWN_STATION` | 인증 전 | 종료 | `station`에 없는 측정소 → `registerStation`으로 등록 |
| `AUTH_FAILED` | 인증 전 | 종료 | 토큰 불일치 |
| `FORBIDDEN` | 인증 후 | 종료 | 인증된 측정소와 다른 `stationId`로 DATA 전송 |
| `MALFORMED` | 인증 후 | 유지 | JSON 구문 오류, 한 줄 4KB 초과 |
| `SCHEMA` | 인증 후 | 유지 | 필수 필드 누락, 타입 불일치, 알 수 없는 type |
| `UNEXPECTED_MESSAGE` | 인증 후 | 유지 | 인증 후 HELLO 재전송 등 |
| `OUT_OF_ORDER` | 인증 후 | 유지, 연속 3회면 종료 | seq가 직전 + 1도, 재전송(같은 값)도 아님 |
| `BAD_TIME` | 인증 후 | 유지 | 정시가 아님, 마지막 저장 시각보다 이전, 서버 시각 + 1일 초과 |
| `OUT_OF_RANGE` | 인증 후 | 유지 | 농도 0~2000 ㎍/㎥, 오존 0~1 ppm 밖. 센서 오작동·조작 값이 경보를 내지 못하게 하는 안전장치 |

거부된 메시지는 저장·판정·통보 어느 단계도 거치지 않으며, 모든 거부는 `connection_reject_log`에 시각·IP·측정소·사유·원문(앞 512자)으로 남는다.
원문의 `token` 값은 가려서 저장한다. 거부된 건의 seq는 소비한 것으로 보아 측정소가 그 건을 건너뛰고 다음 건을 이어 보낼 수 있다.

### 측정소 추가: 서버 코드 수정도 재기동도 없이

`data/은평구.json`은 이 절차를 보여 주기 위해 만든 **가상 데이터**다(세 측정소 시간별 평균에 ±10% 잡음, 점검 구간 2개. 코드 111191은 가상).
`./gradlew makeMockStation --code=111191 --name=은평구`로 다시 만들 수 있고, 시드가 고정된 MT19937을 쓰므로 커밋된 파일과 바이트 단위로 같은 결과가 나온다(테스트로 고정).
`db/seed.sql`에 은평구가 없으므로 처음 실행하면 거부되고, 등록 후 다시 실행하면 수신된다.

```bash
./run-stations.sh 10                       # 은평구만 "UNKNOWN_STATION" 거부, 종료 코드 2
#  → connection_reject_log에 1건 기록

./gradlew registerStation --code=111191 --name=은평구 --token=dev-token-eunpyeong-111191
#  측정소 등록 완료: 111191 은평구          (서버는 계속 떠 있는 상태)

./run-stations.sh 10 data/은평구.json          # 전송 완료 (744건). 경보 33건 기록
```

`registerStation`은 서버의 DB 접속 정보(`DUST_DB_*` 환경변수, 기본값 compose)로 `station`에 직접 upsert한다. 토큰을 바꿀 때도 같은 명령을 쓴다.
운영 토큰은 명령 인자 대신 `--token="$(cat token.txt)"`처럼 파일이나 비밀 저장소에서 읽는 것을 권한다.

---

## 7. 통신 프로토콜

`java.net.ServerSocket`/`Socket` 위에 개행으로 구분한 JSON(NDJSON)을 쓴다. 한 줄이 메시지 하나라 디코더가 단순하고 로그만으로 디버깅이 된다. UTF-8, 한 줄 최대 4KB.

```
→ {"type":"HELLO","stationId":"111311","stationName":"노원구","token":"…","protocol":1,"lastAckedSeq":0}
← {"type":"WELCOME","resumeFromSeq":1}
→ {"type":"DATA","stationId":"111311","seq":17,"measuredAt":"2023-03-01T16:00:00","pm10":34.0,"pm25":24.0,"o3":null}
← {"type":"ACK","seq":17}
→ {"type":"DATA","stationId":"111311","seq":18,"measuredAt":"2023-03-01T17:00:00","pm10":null,"pm25":22.0,"o3":null}
← {"type":"ACK","seq":18}
← {"type":"REJECT","reason":"OUT_OF_RANGE","detail":"seq=19 pm10 허용 범위 0~2000: 5000.0"}
→ {"type":"BYE","stationId":"111311"}
```

- **1초 간격:** n번째 송신 시각은 시작 시각 + n × 간격으로 미리 정해진다(`FixedRateSchedule`). ACK를 기다린 시간이 다음 송신을 밀지 않아 간격이 누적 오차 없이 유지된다. `--interval-ms`로 바꿀 수 있다.
- **ACK와 재전송:** ACK가 3초 안에 오지 않으면 같은 건을 다시 보내고(최대 3회), 그래도 없으면 재접속한다. 서버는 중복을 UNIQUE로 걸러 ACK만 다시 보낸다.
- **재접속:** 연결이 끊기면 1·2·4·8·16·30초 백오프로 재접속하고, 서버가 `WELCOME`으로 알려 준 seq부터 이어 보낸다. 서버가 꺼져 있어도 시뮬레이터는 계속 재시도한다.
- **시뮬레이터 종료 코드:** 0 전송 완료 / 1 실행 불가(인자·파일·토큰) / 2 서버가 측정소를 거부(미등록·토큰 불일치). 미등록·토큰 불일치는 재시도해도 소용없어 즉시 종료하고 등록 명령을 안내한다.

---

## 8. Slack 연동

### Webhook 만들기

Slack 앱 디렉터리에서 **Incoming Webhooks** 앱을 워크스페이스에 추가하거나(api.slack.com/apps → Create New App → Incoming Webhooks → Activate → Add New Webhook to Workspace),
기존 앱에서 Incoming Webhooks를 켜고 채널을 고르면 `https://hooks.slack.com/services/T…/B…/…` 형태의 URL이 생긴다. 이 URL을 `SLACK_WEBHOOK_URL`로 넘긴다.

### 전송 방식: alert_log가 곧 outbox

- 경보 1건이 `alert_log`에 `slack_status = 'PENDING'`으로 커밋되면 그 행이 전송 대기열이다. 별도 메모리 큐가 없어 **서버가 재기동돼도 미전송분이 유실되지 않고** 첫 주기부터 이어서 보낸다.
- `SlackDispatcher`(단일 가상 스레드)가 `slack_next_at`이 지난 가장 오래된 PENDING 1건을 `issued_at` 순으로 꺼내 보내고 결과를 같은 행에 적는다. 한 건 보내면 1초 쉬어 Incoming Webhook의 **초당 1건 제한**을 지킨다. 큐가 비면 1초 대기하되 경보가 커밋되면 이벤트로 즉시 깨어난다.
- 상태 전이: 성공 → `SENT`(`slack_sent_at` 기록) / 5xx·타임아웃 → 1·2·4초 백오프 후 재시도, 3회 넘으면 `FAILED`(`slack_error`에 마지막 응답) / 429 → `Retry-After`만큼 미루고 횟수에 세지 않음 / URL 미설정 → `SKIPPED`.
- Slack이 느리거나 죽어도 수신 → 판정 → 기록 경로는 영향을 받지 않는다. 디스패처 스레드만 기다린다.

### 전송 빈도에 대한 메모

경보 기록 1건마다 1통을 보낸다(묶음 전송 없음). 1시간을 1초로 축소했기 때문에 측정소 세 곳이 동시에 경보 상태면 초당 3건이 생기고 전송은 초당 1건이라
경보가 이어지는 동안 PENDING이 쌓였다가 순차 처리된다. 유실은 없고 지연만 생기며, 이 지연은 시뮬레이션의 산물이다(실제 운영에서는 측정소당 시간당 1통).
10ms 재생처럼 극단적으로 빠르면 세 측정소의 경보 106건이 약 1분 46초에 걸쳐 전송된다.

### 메시지 형식

```
🚨 [미세먼지 경보 · 2등급] 노원구(111311)
측정 시각 2023-03-23 22:00  |  PM10 313 ㎍/㎥  |  2시간 연속 기준 이상
```

Block Kit attachment로 경보(1·2등급)는 빨간 바와 `:rotating_light:`, 주의보(3·4등급)는 주황 바와 `:warning:`을 쓴다. `dust.slack.dashboard-url`을 설정하면 측정소 링크 줄이 붙는다.

---

## 9. 대시보드와 서버 지표

### 대시보드 (GET /)

- 측정소 카드는 `station` 테이블 행 수만큼 생성된다. 아직 접속하지 않은 측정소도 빈 카드로 보이고, 기동 후 `registerStation`으로 등록해 접속한 측정소는 첫 측정값이 오는 순간 JS가 카드를 추가한다.
- 카드에는 최신 PM10·PM2.5, 측정 시각, 마지막 발령 등급 배지, 점검 중 장비 태그, 최근 48시간 차트(기준선 75·150·300)가 있다. 점검 시각은 선이 끊겨 보인다.
- 아래에는 최근 경보 30건(Slack 전송 상태 포함), 점검 내역 20건, 거부 사유별 누적이 있다.
- 최초 렌더링만 DB에서 읽고 이후에는 **GET /stream**(Server-Sent Events)으로 `measurement` · `alert` · `maintenance` 이벤트를 받아 갱신한다. 이벤트는 수신 트랜잭션이 커밋된 뒤에만 발행되므로 화면의 값은 항상 DB에 있는 값이다. 연결이 끊기면 브라우저 `EventSource`가 스스로 재접속한다.
- URL 해시 `#111311`처럼 측정소 코드를 붙이면 그 카드로 스크롤된다. Slack 메시지의 링크가 이 형식이다.
- 차트 라이브러리(Chart.js)는 CDN에서 받는다. 오프라인 환경이면 차트만 비고 나머지는 동작한다.

### 서버 지표 (Actuator)

`GET /actuator/health`와 `GET /actuator/prometheus`만 열어 두었다. 도메인 이벤트를 구독하는 `DustMetrics`가 다음 지표를 만든다.

| 지표 | 종류 | 태그 | 의미 |
|---|---|---|---|
| `dust_connections_active` | gauge | — | 인증된 측정소 접속 수 |
| `dust_messages_received_total` | counter | station | 저장까지 끝난 DATA 수 |
| `dust_messages_rejected_total` | counter | reason | 인증·검증 거부 수 |
| `dust_alerts_issued_total` | counter | station, grade | 경보 발령 수 |
| `dust_slack_deliveries_total` | counter | status | Slack 전송 결과(SENT, RETRY, RATE_LIMITED, FAILED) |

Prometheus가 이 엔드포인트를 스크랩하면 Grafana 패널로 바로 이어질 수 있다. 과제 범위에서는 외부 모니터링 스택 없이 `curl`로 확인 가능한 선까지만 두었다.

---

## 10. 데이터베이스

| 테이블 | 역할 | 핵심 제약 |
|---|---|---|
| `station` | 측정소 마스터와 인증 해시. 측정소 목록의 단일 출처 | PK `station_id`, 평문 토큰 없음 |
| `measurement` | 수신한 측정값 원본. `pm10`/`pm25` NULL = 점검, `o3`는 오존 룰 입력 자리 | `UNIQUE (station_id, measured_at)` → 재전송 멱등 |
| `alert_log` | 시간별 경보 발령 기록 + Slack 전송 상태(outbox) | `UNIQUE (station_id, issued_at)` → 시각당 최고 등급 1건, `grade 1~5` |
| `maintenance_log` | 장비별 점검 구간(연속 null 병합) | `UNIQUE (station_id, equipment, started_at)` |
| `connection_reject_log` | 인증·검증 거부 기록 | — |

- 측정 시각(논리 시간)은 시간대 없는 `TIMESTAMP`, 서버가 찍는 시각(`received_at`, `slack_sent_at` 등)은 `TIMESTAMPTZ`다.
- enum은 `CREATE TYPE` 대신 `VARCHAR + CHECK`로 두어 변경·JDBC 매핑을 단순하게 했다.
- 스크립트 세 개: `db/init.sql`(롤·DB, 재실행 안전, compose가 최초 1회 자동 실행), `db/schema.sql`(테이블·인덱스, `IF NOT EXISTS`만 사용), `db/seed.sql`(초기 측정소 3곳, `ON CONFLICT DO NOTHING`). 서버는 기동마다 `schema.sql`을 실행해 테이블 5개를 확인하고 `seed.sql`로 없는 측정소만 등록한 뒤 수신을 시작한다. 두 번째 기동부터는 아무것도 바꾸지 않으며 DROP 문은 없다. 설정 파일에는 데이터를 두지 않는다.
- PostgreSQL 18 이미지는 데이터 디렉터리가 `/var/lib/postgresql/18/docker`로 바뀌어 compose 볼륨을 `/var/lib/postgresql`에 마운트한다. 예전처럼 `/var/lib/postgresql/data`에 걸면 컨테이너 재생성 시 데이터가 사라진다.

---

## 11. 테스트

```bash
./gradlew test                      # 세 모듈 전체. 105건
./gradlew :central-server:test      # 서버만
```

| 계층 | 도구 | 대상 |
|---|---|---|
| 단위 | JUnit 5, AssertJ | 데이터 파서(결측 4종, 24시, 중복), NDJSON 코덱, `Station.accept()` 경보·점검 판정 18건, 메시지 검증, Slack 포매터, 시뮬레이터 인자·스케줄·백오프, 가상 데이터 생성기(커밋된 파일과 바이트 일치) |
| 통합(DB) | Testcontainers PostgreSQL 18 | DAO 5개, UNIQUE 충돌 처리, 스키마 초기화 재실행 |
| 통합(소켓) | 실제 `ServerSocket` + 테스트 클라이언트 | 인증 실패 3종, HELLO 전 DATA, 소유권, 순서·시각·범위 검증, 세션 교체, 재접속 복원, 거부 기록 |
| 외부 | MockRestServiceServer, Mockito | Webhook 응답 변환(200·429·500·연결 실패), outbox 상태 전이(재시도·FAILED·429·재기동 이어서 전송) |
| 웹 | 실제 HTTP 포트 + JDK HttpClient | 대시보드 렌더링, SSE로 measurement·maintenance·alert 수신, Actuator health·prometheus 노출 |
| 종단 | 시뮬레이터 4개 + 서버 (수동) | 10ms 재생 후 `alert_log`·`maintenance_log` 건수 = 발령 가능성 검토 표 |

Docker가 없는 환경에서는 Testcontainers 기반 테스트가 **자동으로 건너뛰어** 단위 테스트만으로 `./gradlew test`가 통과한다.

---

## 12. 설계 결정

| 쟁점 | 결정 | 이유 |
|---|---|---|
| "2시간 이상 지속"의 시간 기준 | 측정 시각(논리 시간) | 1시간→1초 축소 환경에서 수신 시각으로 재면 의미가 없음 |
| 발령 시점·기록 단위 | 조건 충족 2번째 시각부터, 지속되는 동안 매 시각 1건 | "시간마다 … 기록" 문구 그대로 |
| 기준 비교 | 이상(≥) | "150㎍/㎥ 이상" |
| 한 시각에 여러 기준 충족 | 최고 등급 1건만 기록 | 1 > 2 > 3 > 4 등급 규칙 |
| null이 지속에 미치는 영향 | 끊김(0으로 리셋) | 점검 중엔 측정값이 없어 지속을 입증할 수 없음 |
| 시각 누락 | 끊김 | "연속 2시간" 정의 유지 |
| 점검 기록 단위 | 연속 null 1구간 = 1행, 장비별 분리 | 점검은 사건 단위가 자연스럽고 요구사항이 장비를 구분함. 시간별 원본은 `measurement`에 남음 |
| 오존 주의보 | 룰과 `o3` 필드까지 구현, 데이터 부재로 발령 불가 명시 | "각 경보가 실제 발령 가능한지 검토" 항목의 답 |
| 측정소 등록 | 자동 등록 없음. DB가 단일 출처이고 초기 3곳은 `db/seed.sql`이 없을 때만 넣으며 이후는 `registerStation` | 인증의 의미 유지. 설정 파일에 데이터를 두지 않고, 운영 중 변경이 재기동으로 되돌아가지 않음 |
| 상태 복원 | 저장된 측정값 전체 재생 | 마지막 몇 건만으로는 지속 시간과 룰별 카운터를 정확히 못 살림. 744행이라 비용 미미 |
| Slack 전송 | `alert_log` PENDING 행을 outbox로, 단일 스레드 1건/초 | 외부 장애가 수신을 막지 않고 재기동에도 유실 없음 |
| 중복 수신 | `(station_id, measured_at)` UNIQUE로 멱등 | 재전송·재접속 시 중복 경보 방지 |
| 롤·DB 생성 | 앱 밖(compose initdb 또는 psql), 테이블만 앱이 생성 | 앱에 관리자 자격증명을 넣지 않음. 프로비저닝 영역 |
| 통신 프레이밍 | NDJSON | 한 줄 = 메시지 하나. Netty·Spring Integration은 "소켓 프로그래밍" 조건 취지와 어긋나 미사용 |
| 서버 스레딩 | accept 루프 1개 + 접속당 가상 스레드 | 블로킹 I/O 코드가 그대로 드러나면서 접속 수에 OS 스레드가 비례하지 않음 |
| 시뮬레이터 | Spring 없는 순수 Java, 파일당 프로세스 1개 | 역할이 가볍고 기동이 수백 ms. 측정소는 코드가 아니라 데이터 |

---

## 13. 프로젝트 구조

```
dustwatch/
├ common/            프로토콜 메시지(record, sealed), NdjsonCodec, DustRecordParser
├ station-simulator/ 순수 Java. 진입점(StationSimulator, ExitCode) / cli(인자·주소·콘솔 출력) / transport(송신 루프·접속·고정 주기·백오프)
├ central-server/    Spring Boot
│  └ com.exem.dust
│     ├ domain/      Station(판정 상태), AlertRule, Measurement, MaintenanceWindow, TokenHasher — Spring 의존 없음
│     ├ service/
│     │  ├ ingest/   IngestService(수신 1건 = 트랜잭션 1개), MessageValidator, StationStateLoader
│     │  └ auth/     StationAuthService, RejectRecorder
│     ├ dao/         DAO 인터페이스 5개 + jdbc/ 구현(JdbcClient), DatabaseInitializer(schema.sql·seed.sql 실행)
│     ├ tcp/         TcpServer, ConnectionHandler(세션 상태 기계), SessionRegistry
│     │  └ framing/  BoundedLineReader(4KB 상한 줄 읽기), MessageWriter
│     ├ notify/slack/ SlackDispatcher(outbox), SlackWebhookClient, SlackMessageFormatter
│     ├ web/         DashboardController(GET /), EventStreamController(GET /stream), SseBroadcaster, 화면 DTO
│     ├ metrics/     DustMetrics(이벤트 → Micrometer)
│     ├ report/      FeasibilityReport(발령 가능성 검토)
│     ├ tools/       MockStationGenerator(은평구 가상 데이터 생성기, makeMockStation 태스크), Mt19937
│     ├ admin/       RegisterStationCommand
│     └ config/      DustProperties(dust.*), SlackConfig, TimeConfig
│     record는 각 패키지의 dto/ 하위에 둔다(DecodeResult·IngestResult·AuthResult·SendOutcome·LineRead·MaintenanceChange와 구현 record, 설정·통계 record). SSE 본문은 web/payload/
├ data/              노원구·중구·강서구(제공), 은평구(가상)
├ db/                init.sql(롤·DB), schema.sql(테이블), seed.sql(초기 측정소 3곳)
├ docker-compose.yml postgres:18-alpine
├ .env.example       환경변수 목록과 기본값. 복사해 .env로 쓴다
├ run-server.sh      .env를 읽어 중앙 서버 기동
├ run-stations.sh    측정소 일괄 기동
└ docs/DEVELOPMENT_PLAN.md  개발 계획과 설계 검토 기록
```

의존 방향은 `domain ← service ← dao.jdbc / tcp / notify / web / metrics` 한 방향이고, `domain`에는 Spring import가 없다.
서비스는 도메인 이벤트를 발행만 하고 Slack 디스패처·SSE·지표가 각자 구독한다.

---

## 14. 제한 사항과 운영이라면 달라질 점

- **Slack 묶음 전송 없음.** 요구사항대로 경보 1건 = 1통이다. 운영에서 측정소가 수십 곳이면 시각 단위 요약 메시지나 채널 분리가 필요하다.
- **블로킹 I/O + 가상 스레드.** 측정소 수천 곳이면 NIO나 Netty를 고려하겠지만, 이 과제는 소켓 프로그래밍이 코드에 드러나는 쪽을 택했다.
- **토큰은 사전 공유 키.** 운영이라면 TLS(mTLS 또는 서버 인증서 + 토큰)로 전송 구간을 보호하고 토큰을 주기적으로 교체한다.
- **롤·DB 생성은 앱 밖.** 프로비저닝(IaC) 영역으로 보고 앱에 관리자 자격증명을 두지 않았다.
- **상태 복원은 전체 재생.** 데이터가 수년치로 늘면 최근 N시간만 재생하고 카운터를 스냅숏으로 저장하는 방식으로 바꾼다.
- **스키마 변경.** `IF NOT EXISTS`만으로는 기존 테이블의 컬럼 변경을 반영하지 못한다. 운영이라면 Flyway 같은 마이그레이션 도구로 버전을 관리한다.
- **개발용 토큰 포함.** 평가 편의를 위한 것이며 운영 저장소에는 두지 않는다.
- **외부 모니터링 스택 미포함.** Grafana·Prometheus 프로비저닝은 평가자가 과제 핵심 실행에 추가 구성을 요구받지 않도록 범위에서 뺐다. `/actuator/prometheus`를 스크랩하면 바로 연결된다.
- **대시보드 인증 없음.** 로컬 확인용이다. 운영이라면 인증을 붙이거나 내부망으로 제한한다.
