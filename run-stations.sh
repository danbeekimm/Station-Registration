#!/usr/bin/env bash
# 측정소 시뮬레이터를 데이터 파일 수만큼 띄운다. 파일 하나가 측정소 하나다.
#
#   ./run-stations.sh                      data/*.json 전체, 1초 간격
#   ./run-stations.sh 10                   10ms 간격(빠른 검증, 한 달치가 약 8초)
#   ./run-stations.sh 1000 data/은평구.json  특정 파일만
#
# 환경변수(.env가 있으면 자동으로 읽는다): DUST_SERVER(기본 localhost:${DUST_TCP_PORT:-9000}), STATION_TOKEN_<측정소코드>
set -uo pipefail
cd "$(dirname "$0")"

# Windows(Git Bash): 콘솔 코드 페이지가 949(CP949)면 JVM이 내보내는 UTF-8 한글이 깨지므로 UTF-8(65001)로 바꾼다.
# 콘솔 설정이라 스크립트가 끝난 뒤에도 그 터미널에 유지된다. Linux·macOS에서는 아무것도 하지 않는다.
if [ "${OS:-}" = "Windows_NT" ] && command -v chcp.com >/dev/null 2>&1; then chcp.com 65001 >/dev/null; fi
# JDK 24 이상으로 Gradle을 실행하면 네이티브 접근 WARNING 4줄이 먼저 찍힌다. 동작과 무관하므로 끈다.
export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED${GRADLE_OPTS:+ $GRADLE_OPTS}"

# .env 파일의 값은 이미 export된 환경변수보다 우선하지 않는다(쉘에서 준 값이 이긴다)
if [ -f .env ]; then
  while IFS= read -r line; do
    line="${line%%#*}"; line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" != *=* ]] && continue
    key="${line%%=*}"; value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"       # KEY= value 처럼 = 뒤의 공백은 무시한다
    value="${value#\"}"; value="${value%\"}"   # 양쪽 큰따옴표는 벗긴다
    [ -z "${!key:-}" ] && export "$key=$value"
  done < .env
fi

INTERVAL_MS="${1:-1000}"
shift || true
FILES=("$@")
if [ ${#FILES[@]} -eq 0 ]; then
  FILES=(data/*.json)
fi
SERVER="${DUST_SERVER:-localhost:${DUST_TCP_PORT:-9000}}"

# 개발용 토큰. db/seed.sql의 초기 측정소와 같은 값이며 의도적으로 저장소에 둔다.
# 은평구(111191)는 db/seed.sql에 없어 처음 실행하면 UNKNOWN_STATION으로 거부된다.
# registerStation으로 등록한 뒤 다시 실행하면 서버 수정·재기동 없이 수신된다.
export STATION_TOKEN_111311="${STATION_TOKEN_111311:-dev-token-nowon-111311}"
export STATION_TOKEN_111121="${STATION_TOKEN_111121:-dev-token-junggu-111121}"
export STATION_TOKEN_111212="${STATION_TOKEN_111212:-dev-token-gangseo-111212}"
export STATION_TOKEN_111191="${STATION_TOKEN_111191:-dev-token-eunpyeong-111191}"

BIN="station-simulator/build/install/station-simulator/bin/station-simulator"
echo "시뮬레이터 빌드 확인 중..."
./gradlew -q :station-simulator:installDist || { echo "빌드 실패"; exit 1; }

declare -a PIDS=()
declare -a NAMES=()
for file in "${FILES[@]}"; do
  "$BIN" --file "$file" --server "$SERVER" --interval-ms "$INTERVAL_MS" &
  PIDS+=("$!")
  NAMES+=("$(basename "$file" .json)")
done
echo "측정소 ${#PIDS[@]}개 기동 (서버 $SERVER, 간격 ${INTERVAL_MS}ms). 중단: Ctrl-C"

trap 'kill "${PIDS[@]}" 2>/dev/null' INT TERM

FAILED=0
for index in "${!PIDS[@]}"; do
  wait "${PIDS[$index]}"
  status=$?
  case $status in
    0) echo "[${NAMES[$index]}] 완료" ;;
    2) echo "[${NAMES[$index]}] 서버가 측정소를 거부함 (미등록 또는 토큰 불일치, 종료 코드 2)"; FAILED=1 ;;
    *) echo "[${NAMES[$index]}] 비정상 종료 (코드 $status)"; FAILED=1 ;;
  esac
done
exit $FAILED
