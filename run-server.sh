#!/usr/bin/env bash
# 중앙 서버를 띄운다. 같은 디렉터리의 .env가 있으면 읽어서 환경변수로 넘긴다.
#
#   ./run-server.sh                                  기본 설정(또는 .env)으로 기동
#   SLACK_WEBHOOK_URL=https://hooks.slack.com/... ./run-server.sh
#   ./run-server.sh --args='--dust.slack.webhook-url=https://hooks.slack.com/...'   Gradle bootRun 인자 전달
#
# .env 없이 ./gradlew :central-server:bootRun 을 직접 실행해도 같다. 이 스크립트는 .env 로딩만 더한다.
set -euo pipefail
cd "$(dirname "$0")"

# .env 파일의 값은 이미 export된 환경변수보다 우선하지 않는다(쉘에서 준 값이 이긴다)
if [ -f .env ]; then
  while IFS= read -r line; do
    line="${line%%#*}"; line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" != *=* ]] && continue
    key="${line%%=*}"; value="${line#*=}"
    value="${value#\"}"; value="${value%\"}"   # 양쪽 큰따옴표는 벗긴다
    [ -z "${!key:-}" ] && export "$key=$value"
  done < .env
fi

echo "중앙 서버 기동: HTTP ${DUST_HTTP_PORT:-8080}, TCP ${DUST_TCP_PORT:-9000}, DB ${DUST_DB_HOST:-localhost}:${DUST_DB_PORT:-5432}/${DUST_DB_NAME:-dust}, Slack $([ -n "${SLACK_WEBHOOK_URL:-}" ] && echo 설정됨 || echo 미설정)"
exec ./gradlew :central-server:bootRun "$@"
