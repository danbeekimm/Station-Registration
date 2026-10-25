# 통신 프로토콜, 접속 인증, 메시지 검증, 측정소 추가

## 통신 프로토콜

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

## HELLO 핸드셰이크

측정소는 접속 후 첫 메시지로 `HELLO`(측정소 코드·이름·토큰·프로토콜 버전·마지막 ACK seq)를 보낸다. 서버는 `station` 테이블의 `token_hash`와 **상수 시간 비교**하고,
성공이면 `WELCOME(resumeFromSeq)`로 이어서 보낼 seq를 알려 준다. 접속 후 5초 안에 HELLO가 없거나 HELLO가 아닌 메시지가 먼저 오면 거부 후 소켓을 닫는다.
같은 측정소의 새 접속이 인증을 통과하면 기존 접속을 닫는다(최신 접속 우선). 측정소가 비정상 종료해도 읽기 타임아웃 30초를 기다리지 않고 바로 재접속할 수 있다.

측정소 자동 등록은 하지 않는다. 누구나 소켓만 열면 가짜 측정값을 밀어 넣을 수 있는 구조는 경보 시스템으로서 결함이기 때문이다. `station` 테이블이 측정소 목록과 해시의 유일한 출처고, 서버는 HELLO마다 DB를 읽기 때문에 캐시가 없고 등록 직후 바로 접속된다.

## 거부 사유 코드

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

## 측정소 추가: 서버 코드 수정도 재기동도 없이

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
