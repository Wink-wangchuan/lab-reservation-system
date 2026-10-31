-- ============================================================================
-- v3.5 实验室维度开关：时段共享 / 整间独占
--
-- 背景：v3.4 把预约从「整间独占」改为「按人数共享」后，上课占用的整间机房
--       反而可能被其他单叠加。这里补一个实验室维度的模式开关：
--         share_mode = 1（默认）按人数共享，累计人数不超过容量
--         share_mode = 0 整间独占，时段一相交即拒绝，提示冲突单号
--
-- 内容：
--   1. lab_info 新增 share_mode 字段（已存在则跳过，可重复执行）；
--   2. 同步 3 条知识条目（30019 / 30052 / 30053）的规则描述。
--
-- 幂等：可重复执行。
-- 执行后必做：清 AI 知识库缓存，让新条目立即生效
--   ~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

SET @has_share_mode = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'lab_info'
      AND COLUMN_NAME = 'share_mode'
);

SET @ddl = IF(@has_share_mode = 0,
    'ALTER TABLE lab_info ADD COLUMN share_mode TINYINT NOT NULL DEFAULT 1 COMMENT ''时段占用模式 1可多人共享 0整间独占'' AFTER enable_reserve',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 30019 校验链：补充独占模式例外
UPDATE ai_knowledge
SET content = REPLACE(content,
        '时段容量校验：同一时段允许多单共享，把与该区间相交的有效预约按并发峰值累计人数',
        '时段容量校验：同一时段允许多单共享，把与该区间相交的有效预约按并发峰值累计人数（整间独占的实验室例外：时段相交即拒绝）')
WHERE id = 30019;

-- 30052 冲突口径：独占实验室时段相交即拒绝
UPDATE ai_knowledge
SET content = REPLACE(content,
        '而是按人数共享同一间实验室。',
        '而是按人数共享同一间实验室；标记为「整间独占」的实验室例外，时段相交即拒绝并提示冲突单号。')
WHERE id = 30052;

-- 30053 人数上限口径：独占实验室不按人数共享
UPDATE ai_knowledge
SET content = REPLACE(content,
        '人数上限按「同一时段累计人数」计算，取两道上限中更严格的一个：',
        '人数上限按「同一时段累计人数」计算，取两道上限中更严格的一个；「整间独占」的实验室不按人数共享，时段相交即拒绝：')
WHERE id = 30053;

-- 校验：share_mode 应为 1（默认共享）
SELECT COUNT(*) AS labs_total, SUM(share_mode = 0) AS exclusive_labs FROM lab_info WHERE deleted = 0;
