# 대시보드와 서버 지표

## 대시보드 (GET /)

- 측정소 카드는 `station` 테이블 행 수만큼 생성된다. 아직 접속하지 않은 측정소도 빈 카드로 보이고, 기동 후 `registerStation`으로 등록해 접속한 측정소는 첫 측정값이 오는 순간 JS가 카드를 추가한다.
- 카드에는 최신 PM10·PM2.5, 측정 시각, 마지막 발령 등급 배지, 점검 중 장비 태그, 최근 48시간 차트(기준선 75·150·300)가 있다. 점검 시각은 선이 끊겨 보인다.
- 아래에는 최근 경보 30건(Slack 전송 상태 포함), 점검 내역 20건, 거부 사유별 누적이 있다.
- 최초 렌더링만 DB에서 읽고 이후에는 **GET /stream**(Server-Sent Events)으로 `measurement` · `alert` · `maintenance` 이벤트를 받아 갱신한다. 이벤트는 수신 트랜잭션이 커밋된 뒤에만 발행되므로 화면의 값은 항상 DB에 있는 값이다. 연결이 끊기면 브라우저 `EventSource`가 스스로 재접속한다.
- URL 해시 `#111311`처럼 측정소 코드를 붙이면 그 카드로 스크롤된다. Slack 메시지의 링크가 이 형식이다.
- 차트 라이브러리(Chart.js)는 CDN에서 받는다. 오프라인 환경이면 차트만 비고 나머지는 동작한다.

## 서버 지표 (Actuator)

`GET /actuator/health`와 `GET /actuator/prometheus`만 열어 두었다. 도메인 이벤트를 구독하는 `DustMetrics`가 다음 지표를 만든다.

| 지표 | 종류 | 태그 | 의미 |
|---|---|---|---|
| `dust_connections_active` | gauge | — | 인증된 측정소 접속 수 |
| `dust_messages_received_total` | counter | station | 저장까지 끝난 DATA 수 |
| `dust_messages_rejected_total` | counter | reason | 인증·검증 거부 수 |
| `dust_alerts_issued_total` | counter | station, grade | 경보 발령 수 |
| `dust_slack_deliveries_total` | counter | status | Slack 전송 결과(SENT, RETRY, RATE_LIMITED, FAILED) |

Prometheus가 이 엔드포인트를 스크랩하면 Grafana 패널로 바로 이어질 수 있다. 과제 범위에서는 외부 모니터링 스택 없이 `curl`로 확인 가능한 선까지만 두었다.
