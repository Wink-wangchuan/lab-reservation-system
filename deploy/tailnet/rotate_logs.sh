#!/bin/bash
# ============================================================
# 用户级日志轮转（无 root 权限环境下的替代方案）
#
# systemd 的 StandardOutput=append: 会一直往同一个文件追加，文件只会变大。
# 这里用「复制 + 截断」的方式轮转：写进程持有的仍是同一个 inode，
# 截断后句柄继续从文件头写，磁盘空间立即释放，无需重启服务。
#
# 触发方式：lab-logrotate.timer（每天 03:10，由 systemd user 定时器调用）
# ============================================================
set -euo pipefail

LOG_DIR="${LOG_DIR:-$HOME/lab-reservation/logs}"
MAX_BYTES="${MAX_BYTES:-$((20 * 1024 * 1024))}"   # 单文件超过 20MB 才轮转
KEEP="${KEEP:-7}"                                  # 保留最近 7 份历史
STAMP="$(date +%Y%m%d_%H%M)"

[ -d "$LOG_DIR" ] || exit 0

for file in "$LOG_DIR"/*.log; do
  [ -f "$file" ] || continue
  size="$(stat -c %s "$file")"
  [ "$size" -ge "$MAX_BYTES" ] || continue

  cp "$file" "$file.$STAMP"
  : > "$file"
  echo "[logrotate] $(basename "$file") ${size} bytes -> $(basename "$file").$STAMP"
done

# 清理超出保留份数的历史日志
for prefix in "$LOG_DIR"/*.log; do
  [ -f "$prefix" ] || continue
  # 该前缀对应的历史文件按时间倒序，删掉最旧的
  mapfile -t history < <(ls -1t "$prefix".* 2>/dev/null || true)
  if [ "${#history[@]}" -gt "$KEEP" ]; then
    printf '%s\n' "${history[@]:$KEEP}" | xargs -r rm -f
  fi
done
