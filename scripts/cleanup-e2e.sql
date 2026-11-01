-- ============================================================================
-- 写路径 E2E 清理脚本
--
-- 删除 write-flow.mjs 产生的测试数据（用途以「【E2E】」开头），并级联清理
-- 其签到、审批与站内消息，保持线上库干净。
--
-- 执行：
--   /tmp/sqlvenv/bin/python /tmp/run_sql.py scripts/cleanup-e2e.sql
--   （或 mysql -uroot -p lab_reservation < scripts/cleanup-e2e.sql）
-- ============================================================================

-- 1. 站内消息（biz_id 指向测试预约）
DELETE n FROM sys_notice n
  JOIN lab_reservation r ON r.id = n.biz_id
 WHERE r.purpose LIKE '【E2E】%';

-- 2. 签到凭证
DELETE c FROM lab_checkin c
  JOIN lab_reservation r ON r.id = c.reservation_id
 WHERE r.purpose LIKE '【E2E】%';

-- 3. 审批流水
DELETE a FROM lab_approval a
  JOIN lab_reservation r ON r.id = a.reservation_id
 WHERE r.purpose LIKE '【E2E】%';

-- 4. 预约单本身
DELETE FROM lab_reservation WHERE purpose LIKE '【E2E】%';

-- 5. 顺带清理候补（E2E 若加入候补场景时会用到）
DELETE FROM lab_waitlist WHERE purpose LIKE '【E2E】%';

-- 校验：以下计数应全部为 0
SELECT
    (SELECT COUNT(*) FROM lab_reservation WHERE purpose LIKE '【E2E】%') AS reservations,
    (SELECT COUNT(*) FROM lab_checkin c JOIN lab_reservation r ON r.id = c.reservation_id
      WHERE r.purpose LIKE '【E2E】%') AS checkins;
