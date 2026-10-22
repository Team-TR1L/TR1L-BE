#!/usr/bin/env bash
# Grafana 대시보드를 2배 해상도 PNG로 저장한다 (PR, 문서용)
#   capture.sh <파일 이름> [from] [to]
#   PANEL=2 capture.sh <파일 이름> ...   패널 하나만 저장 (d-solo)
# 예) capture.sh 00-before now-10m now
#     PANEL=2 SIZE=1600,635 capture.sh 02-graph 2026-10-22T07:58:40+09:00 2026-10-22T08:00:30+09:00
# 저장 위치는 CAPTURE_DIR, Chrome 경로는 CHROME으로 바꿀 수 있다
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="${CAPTURE_DIR:-$ROOT/build/reliability-screenshots}"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
mkdir -p "$OUT"
FROM="${2:-now-15m}"; TO="${3:-now}"
# ISO 시각은 epoch ms로 바꿔서 넘긴다
to_ms() { case "$1" in now*) echo "$1" ;; *) python3 -c 'import sys, datetime; print(int(datetime.datetime.fromisoformat(sys.argv[1]).timestamp() * 1000))' "$1" ;; esac; }
RANGE="from=$(to_ms "$FROM")&to=$(to_ms "$TO")"
if [[ -n "${PANEL:-}" ]]; then
  URL="http://localhost:13000/d-solo/job1-reliability/job1-reliability?orgId=1&theme=dark&panelId=${PANEL}&${RANGE}"
  SIZE="${SIZE:-1600,700}"
else
  URL="http://localhost:13000/d/job1-reliability/job1-reliability?orgId=1&kiosk&theme=dark&${RANGE}"
  SIZE="${SIZE:-1600,1280}"
fi
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size="$SIZE" --virtual-time-budget=15000 \
  --screenshot="$OUT/$1.png" "$URL" >/dev/null 2>&1
echo "$OUT/$1.png"
