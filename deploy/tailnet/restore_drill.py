#!/usr/bin/env python3
"""LabWise 备份恢复演练

把最近一份逻辑备份恢复到临时库 lab_reservation_drill，逐表比对行数，
确认备份「可恢复」而不只是「文件存在」。演练结束后自动删除临时库。

建议每月手工跑一次（或在需要验证备份时跑）：
    ~/opt/backup-venv/bin/python ~/lab-reservation/deploy/tailnet/restore_drill.py

退出码：0 表示恢复成功且关键表行数一致；1 表示恢复失败或行数不符。
"""

import gzip
import pathlib
import sys

import pymysql

ENV_FILE = pathlib.Path.home() / "lab-reservation" / ".env"
BACKUP_DIR = pathlib.Path.home() / "lab-backup"
DRILL_DB = "lab_reservation_drill"


def load_env() -> dict:
    cfg = {}
    if not ENV_FILE.exists():
        raise SystemExit(f"配置文件不存在：{ENV_FILE}")
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        cfg[key.strip()] = value.strip().strip('"').strip("'")
    return cfg


def connect(cfg: dict, database: str | None = None) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=cfg.get("DB_HOST", "127.0.0.1"),
        port=int(cfg.get("DB_PORT", "3306")),
        user=cfg.get("DB_USER", "root"),
        password=cfg.get("DB_PASSWORD", ""),
        database=database,
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


def split_statements(sql: str):
    """按行扫描切分语句：备份文件里 INSERT 是单行、DDL 以 ; 结尾，够用且不会误切数据"""
    buffer = []
    for line in sql.splitlines(keepends=True):
        if line.lstrip().startswith("--") or not line.strip():
            continue
        buffer.append(line)
        if line.rstrip().endswith(";"):
            yield "".join(buffer).strip()
            buffer = []
    tail = "".join(buffer).strip()
    if tail:
        yield tail


def table_counts(conn, database: str) -> dict:
    cur = conn.cursor()
    cur.execute("SHOW TABLES")
    tables = [list(row.values())[0] for row in cur.fetchall()]
    counts = {}
    for table in tables:
        cur.execute(f"SELECT COUNT(*) AS c FROM `{database}`.`{table}`")
        counts[table] = cur.fetchone()["c"]
    return counts


def main() -> None:
    backups = sorted(BACKUP_DIR.glob("lab_reservation_*.sql.gz"))
    if not backups:
        raise SystemExit(f"没有可用备份：{BACKUP_DIR}")
    latest = backups[-1]
    print(f"[restore-drill] 使用备份：{latest.name}")

    cfg = load_env()
    live_db = cfg.get("DB_NAME", "lab_reservation")

    admin = connect(cfg)
    cur = admin.cursor()
    cur.execute(f"DROP DATABASE IF EXISTS `{DRILL_DB}`")
    cur.execute(f"CREATE DATABASE `{DRILL_DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
    admin.close()

    drill = connect(cfg, DRILL_DB)
    cur = drill.cursor()
    with gzip.open(latest, "rt", encoding="utf-8") as f:
        for stmt in split_statements(f.read()):
            cur.execute(stmt)
    print("[restore-drill] 恢复完成，开始比对行数")

    live_counts = table_counts(connect(cfg, live_db), live_db)
    drill_counts = table_counts(drill, DRILL_DB)
    drill.close()

    mismatched = []
    for table, live_count in live_counts.items():
        drill_count = drill_counts.get(table)
        if drill_count is None:
            mismatched.append(f"{table}: 备份中缺失（线上 {live_count} 行）")
        elif drill_count != live_count:
            mismatched.append(f"{table}: 线上 {live_count} 行 / 恢复后 {drill_count} 行")

    admin = connect(cfg)
    admin.cursor().execute(f"DROP DATABASE IF EXISTS `{DRILL_DB}`")
    admin.close()

    if mismatched:
        print(f"[restore-drill] 不一致 {len(mismatched)} 项：")
        for item in mismatched:
            print("  - " + item)
        sys.exit(1)

    total = sum(drill_counts.values())
    print(f"[restore-drill] 通过：{len(drill_counts)} 张表 / {total} 行全部一致")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"[restore-drill] 失败：{exc}", file=sys.stderr)
        sys.exit(1)
