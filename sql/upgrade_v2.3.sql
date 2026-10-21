-- ============================================================================
-- v2.3 查询索引优化
--
-- 背景：以下两条查询在业务上高频，但现有索引覆盖不到，会退化为全表扫描/额外排序。
--   1) 实时占用看板与「今日预约」统计：
--        WHERE reserve_date BETWEEN ? AND ? AND status IN (...)
--      既有 idx_reserve_scope 以 lab_id 为前缀，用不上日期+状态条件。
--   2) 站内消息列表：
--        WHERE user_id = ? AND deleted = 0 ORDER BY create_time DESC
--      既有 idx_notice_user(user_id, read_flag) 无法避免按时间排序的 filesort。
--
-- 幂等：MySQL 8 不支持 ADD INDEX IF NOT EXISTS，故用 information_schema 判断后动态执行。
-- ============================================================================

-- 1) 预约：日期 + 状态（看板、今日预约、趋势统计）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'lab_reservation'
                      AND index_name = 'idx_reserve_date_status');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE lab_reservation ADD INDEX idx_reserve_date_status (reserve_date, status)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 站内消息：用户 + 创建时间（消息中心按时间倒序）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'sys_notice'
                      AND index_name = 'idx_notice_user_time');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE sys_notice ADD INDEX idx_notice_user_time (user_id, create_time)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 设备：故障状态 + 实验室（看板一次性取故障实验室、预约时校验锁定）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'device'
                      AND index_name = 'idx_device_fault');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE device ADD INDEX idx_device_fault (fault_status, lab_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验：应返回 3 行
SELECT table_name, index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('idx_reserve_date_status', 'idx_notice_user_time', 'idx_device_fault')
GROUP BY table_name, index_name;
