#!/usr/bin/env bash
# ============================================================================
# 后端健康巡检（systemd user timer 每 5 分钟执行一次）
#
# 逻辑：
#   1. curl /api/health（匿名，只返回 db/redis 状态）；
#   2. 健康则清零失败计数并写一行日志；
#   3. 不健康则累加计数，连续 2 次失败（约 10 分钟）自动重启 lab-backend；
#   4. 只重启后端，不碰 MySQL/Redis/nginx 等其它服务。
# ============================================================================
set -uo pipefail

HOME_DIR="${HOME:-/home/$(id -un)}"
URL="http://100.96.0.100:8091/api/health"
LOG="$HOME_DIR/lab-reservation/logs/health.log"
STATE="$HOME_DIR/lab-reservation/data/health.fail"

mkdir -p "$(dirname "$LOG")" "$(dirname "$STATE")"

body="$(curl -s --max-time 5 "$URL" 2>/dev/null || true)"

if printf '%s' "$body" | grep -q '"status":"UP"'; then
  echo 0 > "$STATE"
  echo "$(date '+%Y-%m-%d %H:%M:%S') UP" >> "$LOG"
  exit 0
fi

fails="$(cat "$STATE" 2>/dev/null || echo 0)"
fails=$((fails + 1))
echo "$fails" > "$STATE"
# 响应可能很长，只截前 160 个字符，避免日志被刷爆
echo "$(date '+%Y-%m-%d %H:%M:%S') DOWN($fails) ${body:0:160}" >> "$LOG"

if [ "$fails" -ge 2 ]; then
  systemctl --user restart lab-backend.service
  echo "$(date '+%Y-%m-%d %H:%M:%S') FAILED twice -> restarted lab-backend" >> "$LOG"
  echo 0 > "$STATE"
fi
