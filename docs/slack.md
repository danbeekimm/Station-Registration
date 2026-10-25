# Slack 연동

## Webhook 만들기

Slack 앱 디렉터리에서 **Incoming Webhooks** 앱을 워크스페이스에 추가하거나(api.slack.com/apps → Create New App → Incoming Webhooks → Activate → Add New Webhook to Workspace),
기존 앱에서 Incoming Webhooks를 켜고 채널을 고르면 `https://hooks.slack.com/services/T…/B…/…` 형태의 URL이 생긴다. 이 URL을 `SLACK_WEBHOOK_URL`로 넘긴다.

## 전송 방식: alert_log가 곧 outbox

- 경보 1건이 `alert_log`에 `slack_status = 'PENDING'`으로 커밋되면 그 행이 전송 대기열이다. 별도 메모리 큐가 없어 **서버가 재기동돼도 미전송분이 유실되지 않고** 첫 주기부터 이어서 보낸다.
- `SlackDispatcher`(단일 가상 스레드)가 `slack_next_at`이 지난 가장 오래된 PENDING 1건을 `issued_at` 순으로 꺼내 보내고 결과를 같은 행에 적는다. 한 건 보내면 1초 쉬어 Incoming Webhook의 **초당 1건 제한**을 지킨다. 큐가 비면 1초 대기하되 경보가 커밋되면 이벤트로 즉시 깨어난다.
- 시도 가능 시각의 비교와 다음 시도 시각 계산은 모두 DB 시계(`now()`)로 한다. 앱과 DB의 시계가 어긋나도 전송 순서가 흔들리지 않는다.
- 상태 전이: 성공 → `SENT`(`slack_sent_at` 기록) / 5xx·타임아웃 → 1·2·4초 백오프 후 재시도, 3회 넘으면 `FAILED`(`slack_error`에 마지막 응답) / 429 → `Retry-After`만큼 미루고 횟수에 세지 않음 / URL 미설정 → `SKIPPED`.
- Slack이 느리거나 죽어도 수신 → 판정 → 기록 경로는 영향을 받지 않는다. 디스패처 스레드만 기다린다.
- 전송 보장은 **최소 1회**다. Webhook이 200을 돌려준 직후 `SENT`를 기록하기 전에 서버가 꺼지면 재기동 후 같은 경보가 한 번 더 갈 수 있다. 유실보다 중복을 택한 것이며, 메시지에 측정 시각이 있어 받는 쪽에서 구분된다.

## 전송 빈도에 대한 메모

경보 기록 1건마다 1통을 보낸다(묶음 전송 없음). 1시간을 1초로 축소했기 때문에 측정소 세 곳이 동시에 경보 상태면 초당 3건이 생기고 전송은 초당 1건이라
경보가 이어지는 동안 PENDING이 쌓였다가 순차 처리된다. 유실은 없고 지연만 생기며, 이 지연은 시뮬레이션의 산물이다(실제 운영에서는 측정소당 시간당 1통).
10ms 재생처럼 극단적으로 빠르면 세 측정소의 경보 106건이 약 1분 46초에 걸쳐 전송된다.

## 메시지 형식

```
🚨 [미세먼지 경보 · 2등급] 노원구(111311)
측정 시각 2023-03-23 22:00  |  PM10 313 ㎍/㎥  |  2시간 연속 기준 이상
```

Block Kit attachment로 경보(1·2등급)는 빨간 바와 `:rotating_light:`, 주의보(3·4등급)는 주황 바와 `:warning:`을 쓴다. `dust.slack.dashboard-url`을 설정하면 측정소 링크 줄이 붙는다.
