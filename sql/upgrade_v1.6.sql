-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.5.0 -> v1.6.0
--
--  目标：把所有实验室的开放时段统一为「学校一般作息」
--
--    周一至周五，每天 4 节（上午两节 + 下午两节）：
--      上午第一节 08:00-09:40    上午第二节 10:00-11:40
--      下午第一节 14:00-15:40    下午第二节 16:00-17:40
--
--  补齐范围：库内全部实验室（含此前未配置时段的 102/103/104）
--  幂等性  ：以「实验室ID + 星期 + 起止时间」为准做反连接（LEFT JOIN ... IS NULL），
--            只插入缺失的组合，已有时段（不论主键是雪花ID还是计算ID）都不会被复制，
--            重复执行结果一致。时段主键 = 实验室ID*1000 + 星期*10 + 节次。
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    set -a && . ~/lab-reservation/.env && set +a
--    ~/opt/jre21/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> \
--        SqlRunner @upgrade_v1.6.sql
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- ----------------------------------------------------------------------------
-- 1. 生成「标准作息 × 全部实验室」的目标时段清单
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
CROSS JOIN (SELECT 1 AS wd UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) d
CROSS JOIN (SELECT 1 AS n, '上午第一节' AS slot_name, '08:00:00' AS st_time, '09:40:00' AS en_time
            UNION ALL SELECT 2, '上午第二节', '10:00:00', '11:40:00'
            UNION ALL SELECT 3, '下午第一节', '14:00:00', '15:40:00'
            UNION ALL SELECT 4, '下午第二节', '16:00:00', '17:40:00') s
WHERE l.deleted = 0;

-- ----------------------------------------------------------------------------
-- 2. 只补齐缺失的时段（反连接：同实验室 + 同星期 + 同起止时间 已存在则跳过）
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
-- 3.1 每个实验室的时段数（统一后应为 20 = 5 天 × 4 节）
SELECT lab_id, COUNT(*) AS slots FROM lab_time_slot WHERE deleted = 0 GROUP BY lab_id ORDER BY lab_id;

-- 3.2 时段总数（16 个实验室 × 20 = 320）
SELECT COUNT(*) AS slots_total FROM lab_time_slot WHERE deleted = 0;

-- 3.3 重复时段检查（应为空）
SELECT lab_id, week_day, start_time, end_time, COUNT(*) AS dup
FROM lab_time_slot
WHERE deleted = 0
GROUP BY lab_id, week_day, start_time, end_time
HAVING dup > 1;

-- 3.4 各节次覆盖情况
SELECT slot_name, COUNT(*) AS cnt FROM lab_time_slot WHERE deleted = 0 GROUP BY slot_name ORDER BY slot_name;
