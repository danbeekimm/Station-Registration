# 설정

모든 값은 `central-server/src/main/resources/application.yml`에 있고, **yml < 환경변수 < 실행 인자** 순으로 덮어쓴다.
환경변수 전체 목록과 기본값은 저장소 루트의 **`.env.example`** 에 있다. `cp .env.example .env`로 복사해 값을 바꾸면 `docker compose`, `run-server.sh`, `run-stations.sh`가 모두 그 파일을 읽는다(`.env`는 커밋되지 않는다).
평가 PC에서 5432·8080·9000이 이미 쓰이고 있으면 `.env`의 `DUST_DB_PORT`, `DUST_HTTP_PORT`, `DUST_TCP_PORT`만 바꾸면 된다. compose 포트 매핑, 서버 접속 URL, 대시보드 링크, 시뮬레이터 접속 주소가 함께 따라간다.

## 설정 항목

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

## 개발용 토큰을 저장소에 포함한 이유

**개발용 토큰은 의도적으로 저장소에 포함했다.** 평가자가 설정 없이 세 줄로 실행할 수 있어야 하기 때문이다.
`db/seed.sql`과 `run-stations.sh`의 기본값이 같은 토큰을 쓰며, DB에는 평문이 아닌 `SHA-256(측정소코드:토큰)` 해시만 저장된다(SQL의 `sha256()`과 Java `TokenHasher`가 같은 값을 내는 것을 테스트로 고정).
운영이라면 서버 쪽 토큰은 `registerStation`으로 바꾸고 시뮬레이터 쪽은 `STATION_TOKEN_<코드>` 환경변수로 맞추며, 실제 Webhook URL은 `.gitignore`에 포함된 `.env`나 환경변수에만 둔다.

## Slack Webhook URL을 환경변수로 두는 이유

Webhook URL은 운영 중 늘어나는 데이터가 아니라 배포 환경에 속한 설정이자 비밀값이라 환경변수로 주입한다. 변경은 재기동으로 반영되는데, 서버 기동은 1초 안에 끝나고 측정소는 재접속해 `WELCOME`이 알려 주는 seq부터 이어 보내며 미전송 Slack 메시지는 `alert_log`에 PENDING으로 남아 재기동 후 이어서 전송되므로 잃는 것이 없다. DB에 두면 비밀값이 평문으로 남고 관리 명령·암호화가 따라붙는 데 비해 얻는 것이 적다.
