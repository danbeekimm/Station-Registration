# dustwatch 개발 계획

미세먼지 실시간 스트리밍 시뮬레이터 및 경보 시스템. 기획안(`엑셈_과제_기획안.pdf`, 2026-10-21)을 기준으로 하되,
실제 데이터 파일과 현재 환경을 확인한 결과를 반영해 바뀐 부분은 **[변경]**, 새로 추가한 결정은 **[추가]**로 표시했다.
작성일 2026-10-25.

---

## 1. 확인된 사실

### 1.1 제공 데이터 (노원구·중구·강서구 3개 파일 실측, 강서구는 2026-10-25 복원본)

| 항목 | 기획안 가정 | 실제 |
|---|---|---|
| 파일 구성 | 파일 1개에 3개 측정소 혼합 | **측정소별 파일 1개** (`노원구.json`, `중구.json`, `강서구.json`) |
| 형식 | JSON 배열, 한글 키 | 동일. 키: `날짜`, `측정소명`, `측정소코드`, `PM10`, `PM2.5` |
| 값 타입 | 모두 문자열 | 숫자는 정수 문자열(`"23"`), **결측은 JSON `null`** (`""`, `"-"` 없음) |
| 행 수 | 744 | 744 (2023-03-01 00 ~ 2023-03-31 23), 시각 누락 0, 중복 0, `24`시 표기 없음 |
| 측정소 코드 | 노원 111311, 중구 111121, 강서 111301 | 노원 **111311**, 중구 **111121**, 강서 **111212** (기획안의 111301과 다름) |

측정소별 결측과 기준 초과 구간:

| 측정소 | PM10 null | PM2.5 null | PM10 최대 | PM2.5 최대 | 2h 이상 ≥300 (PM10) | 2h 이상 ≥150 (PM2.5) |
|---|---|---|---|---|---|---|
| 노원구 | 2h (1구간) | 9h (6구간) | 313 | 111 | 있음 (03-23 21~22) | 없음 |
| 중구 | 7h (2구간) | 7h (2구간, PM10과 같은 시각) | 289 | 114 | 없음 | 없음 |
| 강서구 | 3h (1구간) | 5h (2구간, 3h 구간은 PM10과 같은 시각) | 382 | 121 | 있음 (03-23 19~21, 3h) | 없음 |

### 1.2 기대 결과값 (종단 검증 기준)

룰: 해당 시각과 직전 시각이 연속으로 기준 이상(≥)이면 2번째 시각부터 매 시각 1건, 여러 룰 충족 시 최고 등급 1건.

| 측정소 | alert_log 행 수 | 등급별 | 발령 구간 | maintenance_log 행 수 |
|---|---|---|---|---|
| 노원구 | **32** | 2등급 1, 3등급 22, 4등급 9 | 3등급: 03-05 03(1), 03-11 04~10(7), 03-19 22~03-20 11(14) / 4등급: 03-23 21(1), 03-23 23~03-24 05(7), 03-24 16(1) / **2등급: 03-23 22(1)** | **7** (PM10 1, PM2.5 6) |
| 중구 | **35** | 3등급 28, 4등급 7 | 3등급: 03-05 12(1), 03-10 06~08(3), 03-11 02~10(9), 03-19 21~03-20 11(15) / 4등급: 03-23 19~03-24 01(7) | **4** (PM10 2, PM2.5 2) |
| 강서구 | **39** | 2등급 2, 3등급 23, 4등급 14 | 3등급: 03-11 00~09(10), 03-19 19~03-20 04(10), 03-20 11~13(3) / 4등급: 03-23 18~19(2), 03-23 22~03-24 03(6), 03-24 15~20(6) / **2등급: 03-23 20~21(2)** | **3** (PM10 1, PM2.5 2) |
| 은평구(모방) | 생성 후 산출 | | | |

발령 가능성 결론(현재 데이터 기준): 1등급(초미세먼지 경보) **불가**, 2등급(미세먼지 경보) **노원구 1건·강서구 2건 가능**, 3·4등급 **가능**, 5등급(오존) **데이터 부재로 불가**.
FeasibilityReport가 이 표를 자동 생성해야 하고, 시뮬레이터 종단 실행 결과와 숫자가 일치해야 한다.

### 1.3 환경·버전

| 항목 | 확인 결과 | 조치 |
|---|---|---|
| JDK | temurin 17/21/25 설치. `gradlew`는 PATH의 Homebrew JDK 26으로 기동 | Gradle `java.toolchain = 25`로 컴파일·테스트 JDK 고정 |
| Gradle | 래퍼 9.6.0 | 유지 |
| Spring Boot | 최신 GA **4.1.2** (4.2.0-RC1은 RC) | 4.1.2 사용. `spring-boot-starter-webmvc`(Boot 4 신규 이름) 존재 확인 |
| PostgreSQL | `postgres:18-alpine` = 18.6. **PGDATA가 `/var/lib/postgresql/18/docker`, 볼륨 지점이 `/var/lib/postgresql`로 변경** | compose 볼륨을 `/var/lib/postgresql`에 마운트(구버전 `/data` 경로 쓰면 데이터 유실) |
| Docker | 29.8 (OrbStack) 동작 중 | Testcontainers 사용 가능 |
| Testcontainers / PG JDBC | 1.21.4 / 42.7.14 (Boot BOM 관리) | BOM에 맡김 |

---

## 2. 검토 의견 7건 판정

| # | 지적 | 판정 | 반영 |
|---|---|---|---|
| 1 | 데이터 형식이 가정 상태 | **유효, 해소됨** | 1.1 표로 확정. 측정소별 파일 분리가 유일한 차이 → 시뮬레이터 인자 변경(3절 A) |
| 2 | 토큰 없이 기본 실행 경로 불명확 | **유효** | `run-stations.sh`가 yml 시드와 동일한 개발용 토큰을 기본값으로 내장. `STATION_TOKEN_<코드>` 환경변수로 덮어쓰기. README에 "개발용 토큰 의도적 포함" 명시 |
| 3 | DB·롤 생성이 compose에만 있음 | **유효** | `db/init.sql`(ROLE·DATABASE)과 `db/schema.sql`(테이블)을 분리 제출. compose는 superuser `postgres`로 기동하고 `init.sql`을 `/docker-entrypoint-initdb.d/`에 마운트해 같은 스크립트를 최초 1회 실행. 자체 PG는 `psql -U postgres -f db/init.sql` 1회. 테이블은 앱이 기동마다 생성(3절 L) |
| 4 | 측정소 4곳 이상일 때 동작 | **유효, 기능 데모로 전환** | 은평구는 시드에 **넣지 않는다**. 실행 → `REJECT(UNKNOWN_STATION)` → `registerStation` → 재실행으로 "코드 수정 0, 재기동 0" 시연. 시뮬레이터는 인증 거부 시 종료 코드 2와 안내 문구 출력(3절 F) |
| 5 | 1초 간격 정확성 | **유효(경미)** | 고정 주기: `sendAt(n) = t0 + n·interval`. ACK 대기는 슬롯 안에서, 지연은 누적되지 않음 |
| 6 | 점검 기록 단위 설명 | **유효(문서)** | README 한 줄 추가: 연속 null 1구간 = 1행이며, `measurement`에 시간별 null 원본이 남아 시간 단위 조회도 가능 |
| 7 | .gitignore·커밋 범위 | **유효** | 1번 커밋에 `application-local.yml`, `.env`, `*.local.yml` 무시 규칙. 기존 `.idea` 선택 무시는 `.idea/` 전체 무시로 단순화 |

---

## 3. 기획안 대비 변경·추가 (검토 의견 외에 발견한 것)

- **A. [변경] 시뮬레이터 입력** — `--file`은 측정소 파일 1개. 측정소 코드·이름은 파일 첫 행에서 읽는다. `--station-code`는 선택(혼합 파일 필터·불일치 검증용). `run-stations.sh [interval-ms] [파일...]`은 기본 `data/*.json`을 순회해 파일 수만큼 프로세스를 띄운다.
- **B. [변경] PostgreSQL 16 → 18** — 사용자 지정. 1.3의 볼륨 경로 주의.
- **C. [변경] 세션 상태 복원 방식** — 기획안의 "마지막 측정 2건"으로는 `sustained_hours`(3, 4, 5…)와 룰 5개의 카운터를 정확히 되살릴 수 없다. **해당 측정소의 저장된 측정값 전체(≤744행)를 `Station.replay()`로 다시 흘려** 카운터·직전 시각·진행 중 점검을 복원한다. DB 쓰기 없는 순수 재생이라 코드가 단순하고 결과가 정확하다. 진행 중 점검의 행 id는 `maintenance_log.ended_at IS NULL`에서 읽는다.
- **D. [변경] 강서구 코드** — 복원 파일에서 **111212**로 확인(기획안 111301 아님). yml 시드에 이 값을 쓴다. 은평구는 **111191**(공개 자료로 미확정, 모방 데이터이므로 README에 "가상 코드" 표기).
- **E. [추가] O3 필드** — DATA 메시지와 `measurement`에 선택 필드 `o3`(NUMERIC(6,3) NULL)를 둔다. 오존 주의보 룰이 죽은 코드가 아니라 "구현됐으나 데이터가 없어 발령 불가"가 되도록. 파서는 키가 없으면 null.
- **F. [추가] 시뮬레이터의 인증 거부 처리** — `UNKNOWN_STATION`·`AUTH_FAILED`는 재시도하지 않고 종료 코드 2 + "registerStation으로 등록 후 재실행" 안내. 무한 재시도는 의미가 없고 IP 차단 규칙과 충돌한다.
- **G. [변경] IP 단위 차단 규칙 삭제** — 데모에서 측정소 4개가 모두 127.0.0.1이라 "1분 5회 실패 → 1분 차단"이 정상 측정소까지 막는다. 거부 로그 + HELLO 5초 타임아웃만 유지한다.
- **H. [추가] Docker 없는 환경의 테스트** — Testcontainers 통합 테스트는 `@Testcontainers(disabledWithoutDocker = true)`. 평가자가 Docker 없이 `./gradlew test`를 돌려도 단위 테스트만으로 통과한다.
- **I. [추가] 빈 파일·파싱 실패 처리** — 시뮬레이터는 빈 파일/JSON 오류를 종료 코드 1과 명확한 메시지로 끝낸다(현재 강서구.json이 0바이트인 상태가 바로 이 경로).
- **J. [추가] 은평구 모방 데이터** — `makeMockStation` Gradle 태스크(central-server, DustRecordParser 재사용)로 노원구·중구 시간별 평균에 ±10% 잡음을 섞어 744행 생성. 같은 에피소드(03-19~20 PM2.5, 03-23~24 PM10)를 유지하고 null 구간 2개(PM2.5 단독 3h, 양쪽 동시 2h)를 넣는다. 정수 문자열·JSON null 형식 동일. 생성기와 결과 JSON을 함께 커밋해 재현 가능하게 한다. **1등급 발령을 인위적으로 만들지 않는다**(README의 발령 가능성 검토는 제공 데이터 기준이어야 하므로).
- **K. [유지] 패키지·그룹** — `com.exem.dust`, 루트 프로젝트명 `dustwatch`. 기존 `org.example` 골격은 1번 커밋에서 교체.
- **L. [확정] DB 초기화 책임 분리 (2026-10-25 결정)** — 롤·DB 생성은 앱 밖, 테이블 생성은 앱 안.
  - `db/init.sql`: 롤은 `DO $$ IF NOT EXISTS … CREATE ROLE $$`, DB는 `SELECT 'CREATE DATABASE …' WHERE NOT EXISTS (…)\gexec`로 작성해 두 번 실행해도 안전. compose의 initdb 경로와 psql 경로가 **같은 파일**을 쓴다. compose entrypoint는 볼륨이 비어 있을 때만 실행하므로 재기동 시 덮어쓰지 않는다.
  - `db/schema.sql`: `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`만 사용, DROP 없음. 앱의 SchemaInitializer가 기동마다 실행해 2번째 기동부터는 no-op. 끝나면 "테이블 5개 확인" 로그, 실패 시 기동 중단. 측정소 시드는 `INSERT … ON CONFLICT (station_id) DO UPDATE`.
  - 앱 내 롤·DB bootstrap 옵션은 **넣지 않는다**. 관리자 자격증명을 앱 설정에 요구하고, 접속 설정이 둘로 갈리고, `CREATE DATABASE`의 비트랜잭션·동시 기동 경합 처리가 필요한 데 비해 compose 경로에서는 완전히 중복이다. README "운영이라면 달라질 점"에 "DB·롤 생성은 프로비저닝 영역이라 앱에 두지 않았다"고 명시.
  - 데이터가 지워지는 경로는 `docker compose down -v`뿐이다. 1.3의 PostgreSQL 18 볼륨 경로를 지키지 않으면 컨테이너 재생성 시 데이터가 사라지므로 README에 함께 적는다.

---

## 4. 확정 스택과 구조

- Java 25 (toolchain), Gradle 9.6, Spring Boot 4.1.2, PostgreSQL 18, JdbcTemplate + DAO 인터페이스(JPA 없음), Jackson, Testcontainers, JUnit 5.
- 모듈 3개: `common`(프로토콜·코덱·파서), `station-simulator`(순수 Java), `central-server`(Spring Boot).
- 패키지 구조·프로토콜(NDJSON HELLO/WELCOME/REJECT/DATA/ACK/BYE)·테이블 5개·룰 enum·Slack outbox 디스패처는 기획안 4~10절 그대로.
- 저장소 레이아웃:

```
dustwatch/
├ common/  station-simulator/  central-server/
├ data/            노원구.json  중구.json  강서구.json  은평구.json
├ db/              init.sql(ROLE·DB, compose initdb·psql 공용, 최초 1회)  schema.sql(테이블, 앱이 기동마다 실행)
├ central-server/  makeMockStation 태스크(은평구 가상 데이터 생성기)
├ docker-compose.yml   (postgres:18-alpine, profile grafana는 선택)
├ run-stations.sh
├ docs/DEVELOPMENT_PLAN.md
└ README.md
```

---

## 5. 개발 단계와 커밋 계획

브랜치는 `main` 하나. 커밋 메시지는 `type(scope): 요약` + 본문에 그 커밋의 설계 결정만 적는다. 작업 내용 외의 정보(협업 도구 등)는 넣지 않는다.

### Phase 0 — 준비 (코드 전)

| 순서 | 작업 | 완료 기준 |
|---|---|---|
| 0-1 | 강서구.json 복원 확인 → 코드·행 수·null 확인, 1.2 표 채우기 | 완료 (111212, 744행, 39건/3건) |
| 0-2 | 은평구.json 생성 (3절 J) | 744행, 파서 통과 |
| 0-3 | `git init`, main 브랜치 | |

### Phase 1 — 핵심 경로 (수신 → 판정 → 기록)

| # | 커밋 | 완료 기준 |
|---|---|---|
| 1 | `chore: 멀티모듈 골격, toolchain 25, docker-compose(postgres 18, init.sql 마운트), db/init.sql·schema.sql, .gitignore` | `./gradlew build` 통과. `docker compose up -d` 후 롤 `dust`·DB `dust` 존재 확인. `schema.sql`을 수동으로 두 번 실행해 테이블 5개 생성과 재실행 무오류 확인 |
| 2 | `data: 제공 데이터 3개와 은평구 모방 데이터·생성 스크립트 추가` | 파일 4개, 스크립트 재실행 시 동일 출력 |
| 3 | `feat(common): 프로토콜 메시지, NDJSON 코덱, DustRecordParser` | 파서 테스트(정수·소수·null·키 누락·빈 파일·중복 시각) 통과 |
| 4 | `feat(simulator): 파일 로드, 고정 주기 송신, ACK 대기, 재접속 백오프` | 서버 없이 접속 실패→백오프 로그, 종료 코드 규약(0/1/2) |
| 5 | `feat(server): TCP 서버(가상 스레드), 디코딩, measurement 저장, SchemaInitializer` | 시뮬레이터 1개로 744행 저장, 재전송 시 UNIQUE로 1행 유지 |
| 6 | `feat(alert): AlertRule enum과 Station.accept() 판정` | 파라미터 테스트 8건(경계값·1시간만·null 끊김·시각 누락·동시 충족·등급 상승·지속 기록·O3 미발령) |
| 7 | `feat(alert): alert_log 기록, 멱등, 세션 상태 replay 복원` | 10ms 재생 후 노원 32건·중구 35건 일치. 중간 kill 후 재실행에도 건수 동일 |
| 8 | `feat(maintenance): 장비별 null 구간 병합 저장, 진행 중 점검 복원` | 노원 7건·중구 4건. 재접속 후 null 지속 시 새 행 없이 hours 누적 |

### Phase 2 — 인증·검증·통보·문서

| # | 커밋 | 완료 기준 |
|---|---|---|
| 9 | `feat(auth): HELLO 토큰 인증, yml 시드 upsert, registerStation 태스크, 세션 교체, reject_log` | 소켓 통합 테스트 4건. 은평구 시나리오(거부→등록→수신) 수동 확인 |
| 10 | `feat(validation): DATA 스키마·소유권·순서·시각·범위 검증` | 통합 테스트 3건(seq 건너뜀, pm10=5000, 다른 stationId) |
| 11 | `feat(slack): alert_log outbox 디스패처, Webhook 전송, 재시도·429·FAILED 상태` | MockRestServiceServer 테스트 5건. URL 없으면 SKIPPED, HTTP 호출 0회 |
| 12 | `feat(report): FeasibilityReport (./gradlew feasibility)` | 출력이 1.2 표와 일치 |
| 13 | `docs: README (실행·설정·설계 결정·경보 처리·발령 가능성·점검·인증·스키마·테스트)` | 처음 보는 사람이 README 3줄로 실행 가능. 과제 원문은 요약만 |

### Phase 3 — 선택 (README 완료 후에만)

| # | 커밋 | 완료 기준 |
|---|---|---|
| 14 | `feat(web): Thymeleaf 대시보드 + SSE` | 측정소 카드 자동 생성, 1초마다 차트 갱신 |
| 15 | `feat(metrics): Actuator health/prometheus` | `curl /actuator/health` |
| 16 | `feat(grafana): compose profile, PostgreSQL 데이터소스 프로비저닝` | `--profile grafana`로 패널 3개 |
| 17 | `docs: README 마무리, 제한 사항, 운영이라면 달라질 점` | |

규모 감(참고): Phase 1 ≈ 1.5일, Phase 2 ≈ 1.5일, Phase 3 ≈ 1일. 제출 안정성을 위해 **13번 커밋까지를 제출 가능 상태로 먼저 만든다.**

---

## 6. 종단 검증 시나리오 (제출 전 전부 수행)

1. `docker compose up -d` → `./gradlew :central-server:bootRun` → `./run-stations.sh 10` : 노원·중구·강서 수신 완료, 은평구는 `UNKNOWN_STATION`으로 종료(코드 2).
2. `SELECT station_id, grade, count(*) FROM alert_log GROUP BY 1,2` = 1.2 표. `maintenance_log` 건수 일치.
3. `./gradlew registerStation --code=111191 --name=은평구 --token=...` → `./run-stations.sh 10 data/은평구.json` : 서버 재기동 없이 수신·발령.
4. 시뮬레이터 하나를 중간에 kill → 재실행 → `measurement`·`alert_log` 건수 변화 없음(멱등).
5. 서버를 Slack URL 없이 기동 → `slack_status = SKIPPED`. 실제 Webhook URL로 기동 → 1건/초 전송, Slack 채널에 Block Kit 메시지.
6. 서버를 PENDING이 남은 상태에서 재기동 → 첫 주기부터 이어서 전송.
7. `./gradlew test` : Docker 있는 환경·없는 환경 모두 통과. `./gradlew feasibility` 출력 = README 표.
8. 서버만 2회 재기동 → 테이블·데이터 유지, 로그에 "테이블 5개 확인". `docker compose restart` 후에도 `measurement` 건수 동일. `down -v` 뒤 다시 `up -d` → init.sql이 다시 실행되어 빈 DB부터 재현.

---

## 7. 사용자 결정이 필요한 항목

| 항목 | 추천 | 이유 |
|---|---|---|
| 은평구 데이터 성격 | 현실적 모방(1등급 유도 안 함) | README 검토는 제공 데이터 기준. 데모 목적은 "미등록 측정소 등록 절차" |
| Phase 3 범위 | 대시보드(14)까지, Grafana(16)는 시간 남으면 | 과제 범위 밖. 핵심 경로와 README 품질이 평가 대상 |
| IP 차단 규칙 | 삭제(3절 G) | 동일 IP 다중 측정소 오차단 |
| 세션 복원 | 전체 replay(3절 C) | 2건 복원은 부정확 |
| 패키지 | `com.exem.dust` 유지 | 기획안과 일치 |

달리 지시가 없으면 추천안대로 진행한다.

확정된 결정: DB 초기화 방식은 2026-10-25에 3절 L대로 확정했다(롤·DB는 init.sql 1회, 테이블은 앱이 schema.sql 기동마다 실행, 앱 내 롤·DB 생성 없음).
