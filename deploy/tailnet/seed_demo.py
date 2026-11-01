#!/usr/bin/env python3
"""演示数据生成器（只写带「演示数据」标记的记录，可一键清理）

背景：线上真实预约只有个位数，通过率 / 爽约率 / 趋势曲线没有运营意义。
本脚本按最近 30 天生成一批结构一致的预约、审批与签到记录，既能撑起仪表盘，
又不会和真实数据混淆——所有记录的用途说明都以「【演示数据】」开头，
预约人挂在禁用账号 demo_seed 上，不占用任何真实用户的操作路径。

用法（在服务器上，使用项目已有的备份 venv）：
    ~/opt/backup-venv/bin/python ~/lab-reservation/deploy/seed_demo.py
    ~/opt/backup-venv/bin/python ~/lab-reservation/deploy/seed_demo.py --clean

注意：
  1. 脚本会先清理旧的演示数据，再重新生成，可重复执行；
  2. 只创建「已完成 / 已通过 / 已驳回 / 已取消」四类终态或待准备状态，
     不生成待签到记录，避免定时任务把演示数据推进成爽约并影响真实账号；
  3. 演示数据不参与任何真实账号的禁约、审批与签到逻辑。
"""
import argparse
import os
import random
import sys
from datetime import date, datetime, time, timedelta

import pymysql

ENV_PATH = os.path.expanduser('~/lab-reservation/.env')
MARK = '【演示数据】'
DEMO_USER_ID = 900000000000000100
RES_BASE = 900000000000000000
APPROVAL_BASE = 910000000000000000
CHECKIN_BASE = 920000000000000000
SLOTS = [(time(8, 0), time(9, 40)), (time(10, 0), time(11, 40)),
         (time(14, 0), time(15, 40)), (time(16, 0), time(17, 40))]
PURPOSES = ['课程实验', '毕业设计调试', '社团活动', '课题组例会', '算法训练', '课程答辩预演']


def load_env(path):
    conf = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                conf[key.strip()] = value.strip().strip('"').strip("'")
    return conf


def connect(conf):
    return pymysql.connect(
        host=conf.get('DB_HOST', '127.0.0.1'),
        port=int(conf.get('DB_PORT', '3306')),
        user=conf['DB_USER'],
        password=conf['DB_PASSWORD'],
        database=conf['DB_NAME'],
        charset='utf8mb4',
        autocommit=False,
    )


def clean(cur):
    """按用途标记与固定 ID 区间清理演示数据，不触碰真实记录。"""
    cur.execute(
        "DELETE c FROM lab_checkin c JOIN lab_reservation r ON r.id = c.reservation_id "
        "WHERE r.purpose LIKE %s", (MARK + '%',))
    checkins = cur.rowcount
    cur.execute(
        "DELETE a FROM lab_approval a JOIN lab_reservation r ON r.id = a.reservation_id "
        "WHERE r.purpose LIKE %s", (MARK + '%',))
    approvals = cur.rowcount
    cur.execute("DELETE FROM lab_reservation WHERE purpose LIKE %s", (MARK + '%',))
    reservations = cur.rowcount
    cur.execute("DELETE FROM sys_user WHERE id = %s", (DEMO_USER_ID,))
    users = cur.rowcount
    return checkins, approvals, reservations, users


def next_code(cur):
    """从 99 万段往后取一个未被占用的 6 位核销码，避免与真实核销码冲突。"""
    cur.execute("SELECT checkin_code FROM lab_checkin WHERE checkin_code >= '990000'")
    used = {row[0] for row in cur.fetchall()}
    for n in range(990000, 1000000):
        code = str(n)
        if code not in used:
            used.add(code)
            return code
    raise RuntimeError('核销码段已用尽，请清理演示数据后重试')


def main():
    parser = argparse.ArgumentParser(description='演示数据生成 / 清理')
    parser.add_argument('--clean', action='store_true', help='只清理，不生成')
    args = parser.parse_args()

    conf = load_env(ENV_PATH)
    conn = connect(conf)
    try:
        with conn.cursor() as cur:
            removed = clean(cur)
            if args.clean:
                conn.commit()
                print('已清理演示数据：签到 %d、审批 %d、预约 %d、账号 %d' % removed)
                return

            cur.execute("SELECT id, lab_name, capacity FROM lab_info WHERE deleted = 0 ORDER BY id LIMIT 4")
            labs = cur.fetchall()
            if not labs:
                sys.exit('没有可用实验室，无法生成演示数据')
            cur.execute("SELECT id, real_name FROM sys_user WHERE username = 'admin' AND deleted = 0 LIMIT 1")
            admin = cur.fetchone()
            if not admin:
                sys.exit('找不到 admin 账号')

            approver_id, approver_name = admin
            today = date.today()
            random.seed(20261101)

            # 禁用状态的演示账号：既能作为预约人，又不可能被用来登录
            cur.execute(
                "INSERT INTO sys_user (id, username, password, real_name, gender, status, version, "
                "deleted, create_time, update_time) VALUES (%s, 'demo_seed', 'DEMO_DISABLED', '演示学生', "
                "2, 0, 0, 0, NOW(), NOW())",
                (DEMO_USER_ID,))

            reservations = 0
            approvals = 0
            checkins = 0

            for offset in range(29, -1, -1):
                day = today - timedelta(days=offset)
                for k in range(3):
                    lab_id, lab_name, capacity = labs[k % len(labs)]
                    start, end = SLOTS[(offset + k) % len(SLOTS)]
                    people = max(1, min(18, capacity or 20, random.randint(3, 18)))

                    # 过去日期都落在终态；今天只有 1 条待执行（已通过、不再提醒）
                    if offset == 0 and k == 2:
                        status = 'APPROVED'
                        checkin_status = None
                    else:
                        roll = random.random()
                        if roll < 0.55:
                            status = 'COMPLETED'
                            checkin_status = 'COMPLETED'
                        elif roll < 0.70:
                            status = 'COMPLETED'
                            checkin_status = 'EXPIRED'
                        elif roll < 0.85:
                            status = 'REJECTED'
                            checkin_status = None
                        else:
                            status = 'CANCELLED'
                            checkin_status = None

                    index = reservations + 1
                    res_id = RES_BASE + index
                    created = datetime.combine(day - timedelta(days=1), time(10, 0)) \
                        + timedelta(minutes=random.randint(0, 480))
                    reserve_no = 'DEMO%s%04d' % (day.strftime('%Y%m%d'), index)
                    purpose = MARK + random.choice(PURPOSES)
                    cancel_reason = '演示：临时取消' if status == 'CANCELLED' else None
                    reject_reason = '演示：时段冲突' if status == 'REJECTED' else None

                    cur.execute(
                        "INSERT INTO lab_reservation (id, reservation_no, lab_id, lab_name, student_id, "
                        "student_name, teacher_id, teacher_name, reserve_date, start_time, end_time, purpose, "
                        "people_count, use_device_ids, status, reject_reason, cancel_reason, complete_time, "
                        "reminder_sent, deleted, create_time, update_time) VALUES "
                        "(%s, %s, %s, %s, %s, '演示学生', %s, %s, %s, %s, %s, %s, %s, '', %s, %s, %s, %s, 1, 0, %s, %s)",
                        (res_id, reserve_no, lab_id, lab_name, DEMO_USER_ID, approver_id, approver_name,
                         day, start, end, purpose, people, status, reject_reason, cancel_reason,
                         created + timedelta(hours=8) if status == 'COMPLETED' else None,
                         created, created))
                    reservations += 1

                    if status in ('COMPLETED', 'REJECTED') or (status == 'CANCELLED' and random.random() < 0.5):
                        action = 'REJECT' if status == 'REJECTED' else 'APPROVE'
                        approve_time = created + timedelta(minutes=random.randint(20, 600))
                        cur.execute(
                            "INSERT INTO lab_approval (id, reservation_id, approval_level, approver_id, "
                            "approver_name, action, comment, approve_time) VALUES (%s, %s, 1, %s, %s, %s, %s, %s)",
                            (APPROVAL_BASE + reservations, res_id, approver_id, approver_name, action,
                             '演示数据', approve_time))
                        approvals += 1

                    if checkin_status:
                        checkin_time = datetime.combine(day, start) + timedelta(minutes=random.randint(0, 15))
                        checkout_time = datetime.combine(day, end) if checkin_status == 'COMPLETED' else None
                        cur.execute(
                            "INSERT INTO lab_checkin (id, checkin_code, reservation_id, reservation_no, lab_id, "
                            "lab_name, user_id, user_name, reserve_date, start_time, end_time, status, "
                            "checkin_time, checkout_time, checkin_by_id, checkin_by_name, remark, deleted, "
                            "create_time, update_time) VALUES "
                            "(%s, %s, %s, %s, %s, %s, %s, '演示学生', %s, %s, %s, %s, %s, %s, %s, %s, '演示数据', 0, %s, %s)",
                            (CHECKIN_BASE + reservations, next_code(cur), res_id, reserve_no, lab_id, lab_name,
                             DEMO_USER_ID, day, start, end, checkin_status,
                             checkin_time if checkin_status == 'COMPLETED' else None,
                             checkout_time, approver_id, approver_name, created, created))
                        checkins += 1

            # 未来 3 天：只生成已通过、尚未开始的预约，让「今日预约 / 即将开始」有数据
            for offset in range(1, 4):
                day = today + timedelta(days=offset)
                for k in range(2):
                    lab_id, lab_name, capacity = labs[k % len(labs)]
                    start, end = SLOTS[(offset + k) % len(SLOTS)]
                    people = max(1, min(15, capacity or 20, random.randint(3, 12)))
                    index = reservations + 1
                    res_id = RES_BASE + index
                    created = datetime.now() - timedelta(days=1)
                    reserve_no = 'DEMO%s%04d' % (day.strftime('%Y%m%d'), index)
                    cur.execute(
                        "INSERT INTO lab_reservation (id, reservation_no, lab_id, lab_name, student_id, "
                        "student_name, teacher_id, teacher_name, reserve_date, start_time, end_time, purpose, "
                        "people_count, use_device_ids, status, complete_time, reminder_sent, deleted, "
                        "create_time, update_time) VALUES "
                        "(%s, %s, %s, %s, %s, '演示学生', %s, %s, %s, %s, %s, %s, %s, '', 'APPROVED', NULL, 1, 0, %s, %s)",
                        (res_id, reserve_no, lab_id, lab_name, DEMO_USER_ID, approver_id, approver_name,
                         day, start, end, MARK + random.choice(PURPOSES), people, created, created))
                    reservations += 1
                    cur.execute(
                        "INSERT INTO lab_approval (id, reservation_id, approval_level, approver_id, "
                        "approver_name, action, comment, approve_time) VALUES (%s, %s, 1, %s, %s, 'APPROVE', %s, %s)",
                        (APPROVAL_BASE + reservations, res_id, approver_id, approver_name, '演示数据',
                         created + timedelta(minutes=random.randint(20, 300))))
                    approvals += 1

            conn.commit()
            print('演示数据已生成：预约 %d、审批 %d、签到 %d' % (reservations, approvals, checkins))
            print('清理命令：python seed_demo.py --clean')
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == '__main__':
    main()
