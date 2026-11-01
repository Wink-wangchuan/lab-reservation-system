-- ============================================================================
-- v3.8 工程优化：热点索引补齐 + AI 知识库补充
--
-- 内容：
--   1. lab_reservation 增加 idx_reserve_teacher(teacher_id, reserve_date)：
--      使用率报表「按审批人聚合」与教师端「我审批过的单」都按这两个字段过滤；
--   2. lab_waitlist 增加 idx_wait_status_time(status, create_time)：
--      候补递补任务每 5 分钟按 status=WAITING + create_time 升序取前 100 条，
--      原 idx_wait_scope 以 lab_id 打头，纯 status 过滤用不上；
--   3. 新增 3 条 AI 知识条目（30066-30068：改期 / 设备占用 / 使用率报表）。
--
-- 幂等：可重复执行（索引存在则跳过，知识条目 ON DUPLICATE KEY UPDATE）。
-- 执行后必做：清 AI 知识库缓存
--   ~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

-- ---------- 1. 预约单：审批人 + 日期 ----------
SET @has_teacher_idx = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_reservation'
      AND INDEX_NAME = 'idx_reserve_teacher'
);
SET @ddl = IF(@has_teacher_idx = 0,
    'ALTER TABLE lab_reservation ADD INDEX idx_reserve_teacher (teacher_id, reserve_date)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 候补队列：状态 + 入队时间 ----------
SET @has_wait_idx = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_waitlist'
      AND INDEX_NAME = 'idx_wait_status_time'
);
SET @ddl = IF(@has_wait_idx = 0,
    'ALTER TABLE lab_waitlist ADD INDEX idx_wait_status_time (status, create_time)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. AI 知识库：新增 3 条 ----------
INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES

(30066, '预约流程', '预约提交后还能改时间吗',
 '改期,换时间,换时段,改时间,调整时间,调课,临时有事,推迟,提前,时间冲突',
 '待审核或已通过的预约在开始前可以改期：
1. 入口：我的预约列表里点「改期」，重新选择日期与开放时段。
2. 改期会按新时段重新校验开放时段、闭馆日期、设备故障、整间独占、同人重复与剩余人数，不通过会提示具体原因。
3. 已通过的预约改期后，签到核销码的时段会同步更新，旧时段不再占用容量。
4. 预约开始或结束之后不能再改期，只能重新预约。
5. 改期只能换日期与时段，换实验室需要取消后重新提交。', 66, 1),

(30067, '预约流程', '预约时选了设备，会不会和别人的预约冲突',
 '设备,设备冲突,设备占用,同一台设备,抢设备,设备被占用,选设备,设备编号',
 '设备维度也做了占用校验：
1. 提交预约时如果勾选了具体设备，系统会检查同实验室、同日期、同时段内该设备是否已被其他有效预约占用。
2. 被占用时会提示「所选设备在该时段已被其他预约占用（单号 …）」，需要更换设备或时段。
3. 实验室存在故障设备时整间实验室仍会被锁定，不能通过换设备绕过。
4. 改期时同样会校验设备占用，避免把设备带到冲突时段。', 63, 1),

(30068, '数据统计', '使用率报表在哪里导出',
 '报表,导出,使用率,统计报表,Excel,利用率,审批绩效,时段热度,实验室使用情况',
 '使用率报表用于周期性复盘，入口在「数据驾驶舱」：
1. 点右上角「使用率报表」，默认导出最近 30 天的有效预约（已通过 + 已完成）。
2. 报表包含三页：实验室使用率（场次、人数、时长、场均人数）、时段热度（哪些时段最抢手）、审批绩效（各审批人通过/驳回数量与平均响应时长）。
3. 报表为聚合结果，行数少，导出很快；需要明细请用预约管理里的「导出 Excel」。', 58, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    title = VALUES(title),
    keywords = VALUES(keywords),
    content = VALUES(content),
    priority = VALUES(priority),
    status = VALUES(status);

-- 校验：应返回 2 行
SELECT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_reserve_teacher', 'idx_wait_status_time')
GROUP BY TABLE_NAME, INDEX_NAME;
