#!/usr/bin/env python3
"""LabWise 数据库逻辑备份

服务器没有 mysqldump，且不允許 sudo 安装系统包，这里用 PyMySQL 生成
标准的 SQL 转储文件（DROP + CREATE TABLE + 批量 INSERT），可直接用
`mysql < backup.sql` 恢复。

依赖：~/opt/backup-venv（pymysql），配置读取 ~/lab-reservation/.env。
产物：~/lab-backup/lab_reservation_YYYYmmdd_HHMM.sql.gz，保留最近 7 份。
"""

import datetime
import gzip
import os
import pathlib
import sys

import pymysql

ENV_FILE = pathlib.Path.home() / "lab-reservation" / ".env"
BACKUP_DIR = pathlib.Path.home() / "lab-backup"
KEEP = 7
BATCH = 200


def load_env() -> dict:
    """读取 .env（KEY=VALUE），不依赖外部库"""
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


def dump(conn, out) -> int:
    """写出全部表的 DDL 与数据，返回表数量"""
    cur = conn.cursor()
    cur.execute("SET NAMES utf8mb4")
    cur.execute("SHOW TABLES")
    tables = [list(row.values())[0] for row in cur.fetchall()]
    out.write("SET FOREIGN_KEY_CHECKS=0;\n")

    for table in tables:
        cur.execute(f"SHOW CREATE TABLE `{table}`")
        ddl = cur.fetchone()["Create Table"]
        out.write(f"\nDROP TABLE IF EXISTS `{table}`;\n{ddl};\n")

        cur.execute(f"SELECT * FROM `{table}`")
        rows = cur.fetchall()
        if not rows:
            continue
        columns = ", ".join(f"`{name}`" for name in rows[0].keys())
        for start in range(0, len(rows), BATCH):
            chunk = rows[start:start + BATCH]
            values = []
            for row in chunk:
                items = [conn.escape(v) if v is not None else "NULL" for v in row.values()]
                values.append("(" + ", ".join(items) + ")")
            out.write(f"INSERT INTO `{table}` ({columns}) VALUES " + ",".join(values) + ";\n")

    out.write("SET FOREIGN_KEY_CHECKS=1;\n")
    return len(tables)


def cleanup() -> None:
    """只保留最近 KEEP 份备份，避免磁盘被历史备份占满"""
    files = sorted(BACKUP_DIR.glob("lab_reservation_*.sql.gz"))
    for old in files[:-KEEP]:
        old.unlink(missing_ok=True)


def main() -> None:
    cfg = load_env()
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M")
    target = BACKUP_DIR / f"lab_reservation_{stamp}.sql.gz"

    conn = pymysql.connect(
        host=cfg.get("DB_HOST", "127.0.0.1"),
        port=int(cfg.get("DB_PORT", "3306")),
        user=cfg.get("DB_USER", "root"),
        password=cfg.get("DB_PASSWORD", ""),
        database=cfg.get("DB_NAME", "lab_reservation"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        with gzip.open(target, "wt", encoding="utf-8") as out:
            out.write(f"-- LabWise backup at {datetime.datetime.now()}\n")
            tables = dump(conn, out)
    finally:
        conn.close()

    cleanup()
    size = target.stat().st_size
    print(f"[db-backup] 完成：{target.name}（{tables} 张表，{size} 字节）")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # 定时任务里需要非零退出码供 systemd 记录失败
        print(f"[db-backup] 失败：{exc}", file=sys.stderr)
        sys.exit(1)
