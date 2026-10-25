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
| 테스트 | JUnit 5, AssertJ, Testcontainers(PostgreSQL 18), MockRestServiceServer — 111건 |

---

## 1. 실행 방법

### 사전 준비

- Docker (PostgreSQL 컨테이너와 통합 테스트용).
- **JDK 25** (JAVA_HOME 또는 PATH). Gradle·서버·시뮬레이터가 모두 이 JDK로 실행된다
- Windows에서는 **cmd·PowerShell·Git Bash 어느 터미널이든** 된다. cmd·PowerShell은 `.\run-server.cmd`·`.\run-stations.cmd`, Git Bash는 `./run-server.sh`·`./run-stations.sh`를 쓴다(인자·동작 동일). 둘 다 콘솔을 UTF-8(코드 페이지 65001)로 바꿔 한글 출력이 깨지지 않게 하고, JDK 24 이상에서 Gradle이 먼저 찍는 네이티브 접근 WARNING도 끈다. 아래의 `./gradlew`는 cmd·PowerShell에서 `.\gradlew.bat`로 읽는다. 직접 쓸 때 한글이 깨지면 먼저 `chcp 65001`을 실행한다
- Slack 알림을 보려면 Incoming Webhook URL (선택. 없어도 전체 흐름이 동작한다)

### 세 단계 실행

포트가 겹치거나 Slack Webhook URL을 설정하려면 `.env` 파일에서 설정한다.

```bash
# 1. PostgreSQL 18 기동 — 최초 1회 db/init.sql이 롤 dust와 DB dust를 만든다 (.env의 DUST_DB_PORT를 읽는다)
docker compose up -d

# 2. 중앙 서버 기동 — 기동마다 db/schema.sql(테이블 보장)과 db/seed.sql(측정소 3곳, 없는 것만 등록)을 실행한다
./run-server.sh                      # .env를 읽어 ./gradlew :central-server:bootRun 을 실행한다
#    Windows cmd·PowerShell:  .\run-server.cmd
#    Slack으로 보내려면:  SLACK_WEBHOOK_URL=https://hooks.slack.com/services/... ./run-server.sh   (또는 .env에 적는다)

# 3. 다른 터미널에서 측정소 시뮬레이터 기동 — data/*.json 파일마다 프로세스 1개
./run-stations.sh          # 1초 간격 (원본 1시간 → 1초). 한 달치 744건에 약 12분 24초
./run-stations.sh 10       # 10ms 간격. 빠른 검증용, 약 8초
#    Windows cmd·PowerShell:  .\run-stations.cmd  /  .\run-stations.cmd 10
```

시뮬레이터 터미널에는 `[111311 노원구] seq=17 2023-03-01T16 pm10=34.0 pm25=24.0 → ACK` 형태로 한 줄씩 찍히고,
서버 로그에는 경보 발령·점검 시작/종료·Slack 전송 결과가 남는다.
`run-stations.sh`는 은평구(111191)가 서버에 등록되지 않은 측정소라 **의도적으로 거부**되는 것까지 보여 준다(2절 "측정소 추가").
시뮬레이터가 `[경고] 연결 문제: Connection refused`와 `N초 후 재접속`만 반복하면 **중앙 서버가 아직 9000 포트를 열지 않은 것**이다(2단계를 건너뛰었거나 기동 중). 서버 터미널에 `TCP 수신 시작: 포트 9000`이 찍힌 뒤 시뮬레이터를 띄운다. 시뮬레이터는 1·2·4·8·16·30초 간격으로 계속 재접속하므로 그대로 두어도 서버가 뜨는 순간 이어진다.

### 결과 확인

브라우저로 **http://localhost:8080** 을 열면 측정소 카드(최신 농도·경보 등급·점검 중 장비·차트)와 경보·점검 피드가 1초마다 갱신된다.
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

### 데이터를 지우고 처음부터

```bash
docker compose down -v && docker compose up -d     # 볼륨까지 삭제. 재기동(restart, down 없이 up)만으로는 데이터가 유지된다
```

### 설정

모든 값은 `application.yml`에 있고 **yml < 환경변수 < 실행 인자** 순으로 덮어쓴다. 환경변수 전체 목록은 `.env.example`에 있으며, 5432·8080·9000이 이미 쓰이는 PC에서는 `.env`의 `DUST_DB_PORT`, `DUST_HTTP_PORT`, `DUST_TCP_PORT`만 바꾸면 compose·서버·시뮬레이터가 함께 따라간다. 개발용 토큰은 평가자가 설정 없이 실행할 수 있도록 의도적으로 저장소에 포함했다.
자세히: [docs/configuration.md](docs/configuration.md)

---

## 2. 전체 흐름

### 수신에서 통보까지

1. **접속·인증.** 측정소가 TCP로 접속해 첫 메시지로 `HELLO`(측정소 코드, 토큰, 마지막 ACK seq)를 보낸다. 서버는 `station` 테이블의 토큰 해시와 상수 시간 비교하고, 통과하면 저장된 측정값으로 판정 상태를 복원한 뒤 `WELCOME(resumeFromSeq)`로 이어서 보낼 지점을 알린다. 미등록 측정소나 토큰 불일치는 `REJECT` 후 소켓을 닫고 `connection_reject_log`에 남긴다.
2. **검증.** `DATA` 한 건마다 필수 필드 → 소유권(인증된 측정소와 같은 코드) → 순서(seq 연속 또는 재전송) → 시각(정시, 역행 금지, 미래 1일 이내) → 범위(0~2000㎍/㎥)를 검사한다. 거부된 건은 저장·판정·통보 어느 단계도 거치지 않는다.
3. **저장·판정·기록.** 한 트랜잭션에서 `measurement`에 저장하고(같은 시각이 이미 있으면 중복으로 보고 ACK만 재전송), `Station.accept()`가 룰별 지속 카운터와 장비별 점검 구간을 갱신해 그 시각의 발령(있다면)과 점검 변화를 돌려주면 `alert_log`·`maintenance_log`에 기록한다. 그 뒤 `ACK`를 보낸다.
4. **통보·표시.** 커밋 뒤 도메인 이벤트가 발행되고, `alert_log`의 PENDING 행을 outbox로 쓰는 Slack 디스패처가 초당 1건씩 Webhook으로 보내 결과를 같은 행에 적는다. 대시보드는 같은 이벤트를 SSE로 받아 1초마다 갱신되고, Actuator 지표도 같은 이벤트로 집계된다.

측정소 시뮬레이터는 파일 하나를 읽어 n번째 건을 시작 시각 + n초에 보낸다(ACK 대기가 다음 송신을 밀지 않음). ACK가 3초 안에 없으면 같은 건을 재전송하고, 연결이 끊기면 백오프 후 재접속해 서버가 알려 준 seq부터 이어 보낸다.
자세히: [docs/protocol-and-security.md](docs/protocol-and-security.md), [docs/slack.md](docs/slack.md), [docs/dashboard-and-metrics.md](docs/dashboard-and-metrics.md)

### 점검 내역

측정값 `null`은 해당 장비(PM10, PM2.5를 따로)가 점검 중이라는 뜻으로 보고, **연속 null 구간 하나를 `maintenance_log` 1행**으로 저장한다. 시작 시각·재개 시각·시간 수를 기록하며 마지막 데이터가 null로 끝나면 진행 중으로 남는다. 시간별 원본 null은 `measurement`에 그대로 있어 시각 단위로도 조회할 수 있다.
자세히: [docs/database.md](docs/database.md)

### 측정소 추가: 서버 수정도 재기동도 없이

`station` 테이블이 측정소의 단일 출처고 서버는 HELLO마다 DB를 읽는다. `data/은평구.json`(가상 데이터)으로 절차를 보일 수 있다.

```bash
./run-stations.sh 10                                      # 은평구만 UNKNOWN_STATION으로 거부(종료 코드 2)
./gradlew registerStation --code=111191 --name=은평구 --token=dev-token-eunpyeong-111191
./run-stations.sh 10 data/은평구.json                      # 744건 수신, 경보 33건
```

Windows cmd·PowerShell에서는 `.\run-stations.cmd 10`, `.\gradlew.bat registerStation ...`, `.\run-stations.cmd 10 data\은평구.json`이다.

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
HELLO 인증 직후 그 측정소의 저장된 측정값을 시각순으로 다시 `accept()`해 상태를 정확히 복원한다.
재생은 **마지막 평온 시각**(모든 농도가 가장 낮은 기준 미만이고 결측도 없는 가장 최근 시각)부터 한다. 그 시각을 처리하면 룰별 카운터가 모두 0이 되고 진행 중 점검도 닫히므로 전체 재생과 결과가 같고, 이력이 길어져도 재생량은 최근 고농도·점검 구간 길이에만 비례한다.
그래서 재접속 뒤 이어진 시각의 `sustained_hours`가 끊기지 않고, 진행 중이던 점검도 새 행 없이 같은 행에 누적된다.

---

## 4. 발령 가능성 검토 (제공 데이터 기준)

손으로 세지 않고 `./gradlew -q feasibility`가 서버와 **같은 파서·룰 엔진**으로 데이터 파일을 훑어 계산한다.
"기록 건수"는 시각마다 최고 등급 1건만 남기는 `alert_log` 기준이라, 시뮬레이터로 재생한 뒤의 DB 건수와 같아야 하며 실제로 같음을 확인했다.
**은평구는 제공 데이터가 아닌 가상 데이터**(2절)이므로 발령 가능성 결론은 노원구·중구·강서구 세 측정소로 판단한다.

### 결론

| 등급 | 경보 | 제공 데이터에서 실제 발령 가능? | 근거 |
|---|---|---|---|
| 1 | 초미세먼지 경보 | **불가** | PM2.5 ≥150이 한 시간도 없음. 최댓값 강서구 121(03-19 23시), 중구 114, 노원구 111 |
| 2 | 미세먼지 경보 | **가능** — 강서구 2건, 노원구 1건 | 강서구 03-23 19~21시 375·382·325로 3시간 연속 ≥300 → 20·21시 발령. 노원구 03-23 21~22시 307·313 → 22시 발령. 중구는 최댓값 289로 미달 |
| 3 | 초미세먼지 주의보 | **가능** — 73건 | 03-10~11, 03-19~20에 세 측정소 모두 장시간 ≥75. 중구는 16시간 연속 |
| 4 | 미세먼지 주의보 | **가능** — 30건 | 03-23~24 고농도 사건. 2등급이 발령된 시각은 4등급 조건도 충족하지만 상위 등급으로 기록 |
| 5 | 오존 주의보 | **불가** | 제공 데이터에 오존 측정값이 없다. 룰은 구현되어 있고(`O3_WATCH`, 1시간 지속) DATA 메시지에 `o3` 필드도 있어 값이 오면 동작한다 |

### 측정소별 기록 건수 (`./gradlew -q feasibility` 요약)

| 등급 | 경보 단계 | 기준 | 강서구 | 노원구 | 중구 | 은평구(가상) |
|---|---|---|---|---|---|---|
| 1 | 초미세먼지 경보 | PM2.5 ≥ 150 ㎍/㎥ 2시간 이상 | 0건 | 0건 | 0건 | 0건 |
| 2 | 미세먼지 경보 | PM10 ≥ 300 ㎍/㎥ 2시간 이상 | 2건 | 1건 | 0건 | 2건 |
| 3 | 초미세먼지 주의보 | PM2.5 ≥ 75 ㎍/㎥ 2시간 이상 | 23건 | 22건 | 28건 | 23건 |
| 4 | 미세먼지 주의보 | PM10 ≥ 150 ㎍/㎥ 2시간 이상 | 14건 | 9건 | 7건 | 8건 |
| 5 | 오존 주의보 | O3 ≥ 0.12 ppm 1시간 이상 | 0건 | 0건 | 0건 | 0건 |
| 합계 | | | 39건 | 32건 | 35건 | 33건 |

측정소별 룰 통계(조건 충족 시간, 최장 연속 구간, 최댓값), 점검 구간, 결측 표현, 데이터 특성 메모: [docs/feasibility-report.md](docs/feasibility-report.md)

---

## 5. 프로젝트 구조

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
├ run-server.sh      .env를 읽어 중앙 서버 기동 (Git Bash)
├ run-stations.sh    측정소 일괄 기동 (Git Bash)
├ run-server.cmd     Windows cmd·PowerShell용. run-server.ps1을 실행 정책 설정 없이 띄우는 래퍼
├ run-stations.cmd   Windows cmd·PowerShell용. run-stations.ps1 래퍼
└ docs/              아래 문서들
```

의존 방향은 `domain ← service ← dao.jdbc / tcp / notify / web / metrics` 한 방향이고, `domain`에는 Spring import가 없다.
서비스는 도메인 이벤트를 발행만 하고 Slack 디스패처·SSE·지표가 각자 구독한다.

---

## 6. 테스트

```bash
./gradlew test                      # 세 모듈 전체. 111건
```

단위(파서·코덱·판정 로직·검증·포매터·생성기), 통합(Testcontainers PostgreSQL 18으로 DAO·소켓·outbox·웹) 테스트로 나뉘며, Docker가 없는 환경에서는 통합 테스트가 자동으로 건너뛰어 단위 테스트만으로 통과한다. 구성과 대상: [docs/design-decisions.md](docs/design-decisions.md#테스트)

---

## 7. 문서

| 문서 | 내용 |
|---|---|
| [docs/configuration.md](docs/configuration.md) | 설정 항목 전체, 환경변수 우선순위, 개발용 토큰과 Webhook URL을 그렇게 둔 이유 |
| [docs/protocol-and-security.md](docs/protocol-and-security.md) | NDJSON 프로토콜, HELLO 핸드셰이크, 거부 사유 코드표, 측정소 추가 절차 |
| [docs/database.md](docs/database.md) | 테이블 5개, 스크립트 3개와 자동 실행, 인덱스와 쿼리 근거, 점검 내역 기록 규칙 |
| [docs/slack.md](docs/slack.md) | Webhook 만들기, outbox 설계와 상태 전이, 최소 1회 보장, 전송 빈도, 메시지 형식 |
| [docs/dashboard-and-metrics.md](docs/dashboard-and-metrics.md) | 대시보드·SSE 동작, Actuator 지표 표 |
| [docs/feasibility-report.md](docs/feasibility-report.md) | `./gradlew feasibility` 전체 출력(측정소별 룰 통계·점검 구간), 데이터 특성 메모 |
| [docs/design-decisions.md](docs/design-decisions.md) | 설계 결정 표, 테스트 구성, 제한 사항과 운영이라면 달라질 점 |
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | 개발 계획, 검토 의견 판정, 진행 결과와 구현 중 발견한 사항 |
