-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.6.0 -> v1.7.0
--
--  目标：在统一作息基础上补充周末开放规则
--
--    周一至周五：4 节（上午两节 + 下午两节）        —— 保持不变
--    周六      ：全天 4 节                          —— 新增
--    周日      ：仅上午 2 节（下午休息）            —— 新增
--
--  补齐范围：库内全部实验室，每个实验室共 5×4 + 4 + 2 = 26 条
--  幂等性  ：沿用 v1.6 的做法——先算出目标清单，再用 LEFT JOIN 反连接只补缺失组合，
--            已有时段（含周一至周五）不会被复制；重复执行结果一致。
--            时段主键 = 实验室ID*1000 + 星期*10 + 节次。
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    set -a && . ~/lab-reservation/.env && set +a
--    ~/opt/jre21/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> \
--        SqlRunner @upgrade_v1.7.sql
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- ----------------------------------------------------------------------------
-- 1. 生成目标清单（星期 → 该星期开放的节次数：周六 4 节、周日 2 节）
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_slot_plan;

CREATE TEMPORARY TABLE tmp_slot_plan (
    id         BIGINT       NOT NULL,
    lab_id     BIGINT       NOT NULL,
    week_day   TINYINT      NOT NULL,
    slot_name  VARCHAR(64)  NOT NULL,
    st_time    TIME         NOT NULL,
    en_time    TIME         NOT NULL,
    max_people INT          NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

INSERT INTO tmp_slot_plan
SELECT l.id * 1000 + d.wd * 10 + s.n,
       l.id, d.wd, s.slot_name, s.st_time, s.en_time, l.capacity
FROM lab_info l
CROSS JOIN (SELECT 1 AS wd, 4 AS max_n
            UNION ALL SELECT 2, 4
            UNION ALL SELECT 3, 4
            UNION ALL SELECT 4, 4
            UNION ALL SELECT 5, 4
            UNION ALL SELECT 6, 4   -- 周六：全天
            UNION ALL SELECT 7, 2   -- 周日：仅上午
           ) d
CROSS JOIN (SELECT 1 AS n, '上午第一节' AS slot_name, '08:00:00' AS st_time, '09:40:00' AS en_time
            UNION ALL SELECT 2, '上午第二节', '10:00:00', '11:40:00'
            UNION ALL SELECT 3, '下午第一节', '14:00:00', '15:40:00'
            UNION ALL SELECT 4, '下午第二节', '16:00:00', '17:40:00') s
WHERE l.deleted = 0
  AND s.n <= d.max_n;

-- ----------------------------------------------------------------------------
-- 2. 只补齐缺失的时段（同实验室 + 同星期 + 同起止时间 已存在则跳过）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO lab_time_slot
(id, lab_id, week_day, slot_name, start_time, end_time, max_people, status, deleted, create_time, update_time)
SELECT p.id, p.lab_id, p.week_day, p.slot_name, p.st_time, p.en_time, p.max_people, 1, 0, NOW(), NOW()
FROM tmp_slot_plan p
LEFT JOIN lab_time_slot t
       ON t.lab_id = p.lab_id
      AND t.week_day = p.week_day
      AND t.start_time = p.st_time
      AND t.end_time = p.en_time
      AND t.deleted = 0
WHERE t.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_slot_plan;

-- ----------------------------------------------------------------------------
-- 3. 校验
-- ----------------------------------------------------------------------------
-- 3.1 每个实验室时段数（应为 26 = 周一至周五 20 + 周六 4 + 周日 2）
SELECT lab_id, COUNT(*) AS slots FROM lab_time_slot WHERE deleted = 0 GROUP BY lab_id ORDER BY lab_id;

-- 3.2 时段总数（16 × 26 = 416）
SELECT COUNT(*) AS slots_total FROM lab_time_slot WHERE deleted = 0;

-- 3.3 按星期分布（周六应为 16×4=64，周日应为 16×2=32）
SELECT week_day, COUNT(*) AS cnt FROM lab_time_slot WHERE deleted = 0 GROUP BY week_day ORDER BY week_day;

-- 3.4 重复时段检查（应为空）
SELECT lab_id, week_day, start_time, end_time, COUNT(*) AS dup
FROM lab_time_slot
WHERE deleted = 0
GROUP BY lab_id, week_day, start_time, end_time
HAVING dup > 1;
