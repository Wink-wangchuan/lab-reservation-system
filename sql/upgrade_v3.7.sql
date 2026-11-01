-- ============================================================================
-- v3.7 预约闭环增强：开始前提醒、爽约信用、候补队列、闭馆日历
--
-- 内容：
--   1. sys_user 增加 no_show_count / no_show_ban_until（爽约信用）；
--   2. 新增 lab_closure（闭馆日历）与 lab_waitlist（候补队列）两张表；
--   3. 新增 3 条 AI 知识条目（30063-30065）。
--
-- 幂等：可重复执行（字段/表存在则跳过，知识条目 ON DUPLICATE KEY UPDATE）。
-- 执行后必做：清 AI 知识库缓存
--   ~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

SET @has_no_show = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'no_show_count'
);
SET @ddl = IF(@has_no_show = 0,
    'ALTER TABLE sys_user ADD COLUMN no_show_count INT NOT NULL DEFAULT 0 COMMENT ''连续爽约次数(签到成功清零)'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_ban = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'no_show_ban_until'
);
SET @ddl = IF(@has_ban = 0,
    'ALTER TABLE sys_user ADD COLUMN no_show_ban_until DATETIME DEFAULT NULL COMMENT ''连续爽约后的禁约截止时间'' AFTER no_show_count',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reminder = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_reservation' AND COLUMN_NAME = 'reminder_sent'
);
SET @ddl = IF(@has_reminder = 0,
    'ALTER TABLE lab_reservation ADD COLUMN reminder_sent TINYINT NOT NULL DEFAULT 0 COMMENT ''开始前提醒是否已发送 0未发 1已发'' AFTER complete_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS lab_closure (
    id          BIGINT       NOT NULL COMMENT '主键ID(雪花)',
    lab_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '实验室ID，0 表示全部实验室',
    start_date  DATE         NOT NULL COMMENT '闭馆开始日期',
    end_date    DATE         NOT NULL COMMENT '闭馆结束日期(含)',
    reason      VARCHAR(255) DEFAULT NULL COMMENT '闭馆原因',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_closure_scope (lab_id, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实验室闭馆日历表';

CREATE TABLE IF NOT EXISTS lab_waitlist (
    id           BIGINT       NOT NULL COMMENT '主键ID(雪花)',
    lab_id       BIGINT       NOT NULL COMMENT '实验室ID',
    lab_name     VARCHAR(128) DEFAULT NULL COMMENT '实验室名称(冗余快照)',
    reserve_date DATE         NOT NULL COMMENT '预约日期',
    start_time   TIME         NOT NULL COMMENT '开始时间',
    end_time     TIME         NOT NULL COMMENT '结束时间',
    user_id      BIGINT       NOT NULL COMMENT '排队用户ID',
    user_name    VARCHAR(64)  DEFAULT NULL COMMENT '排队用户姓名(冗余快照)',
    people_count INT          NOT NULL DEFAULT 1 COMMENT '使用人数',
    purpose      VARCHAR(512) DEFAULT NULL COMMENT '用途说明',
    status       VARCHAR(16)  NOT NULL DEFAULT 'WAITING'
        COMMENT '状态 WAITING等待中/PROMOTED已递补/CANCELLED已取消/EXPIRED已失效',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_wait_scope (lab_id, reserve_date, status),
    KEY idx_wait_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实验室候补队列表';

-- ============ AI 知识库：新增 3 条 ============
INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES

(30063, '预约流程', '时段约满后可以排队候补吗',
 '候补,排队,约满,满了,约不上,自动递补,等待队列,候补成功,活补,捡漏,退单,有人取消',
 '时段约满后可以加入候补队列：
1. 只有在累计人数确实超出容量或时段上限时才允许排队，仍有余量会提示直接预约。
2. 排队顺序按加入时间先后（先来先服务）。
3. 系统每 5 分钟检查一次：有人取消或驳回释放容量后，按顺序自动为排队用户提交一条待审核预约，并推送「候补成功」消息。
4. 时段已过、实验室关闭或闭馆、账号处于爽约限制期时，候补会自动失效并通知本人。
5. 在「我的预约 → 我的候补」可以查看状态并主动退出队列。', 65, 1),

(30064, '实验室管理', '节假日 / 临时闭馆怎么配置',
 '闭馆,闭馆日历,节假日,放假,国庆,考试周,临时关闭,停用日期,不让预约,闭馆配置,维护',
 '闭馆日历用于按日期停用实验室，优先级高于按星期的开放时段：
1. 入口：开放时段配置页下方的「闭馆日历」。
2. 可以选择「全部实验室」或某个实验室，并指定起止日期与原因。
3. 命中闭馆日期时，预约提交会被拒绝并提示「该实验室当日闭馆（原因）」，可预约时段查询也会直接提示闭馆。
4. 闭馆只影响对应日期，不影响其他日期的正常排期。', 60, 1),

(30065, '预约流程', '预约开始前有提醒吗，不去会有什么后果',
 '提醒,开始前,快到了,通知,爽约,没去,不去,迟到,未签到,限制预约,禁止预约,信用,后果',
 '预约开始前的提醒与爽约规则：
1. 已通过的预约会在开始前 30 分钟内推送一次站内消息提醒，每条预约只提醒一次；已签到的不会再提醒。
2. 到结束时刻仍未签到的签到单会自动置为「已过期」，同时记一次爽约。
3. 连续爽约满 3 次会被限制预约 7 天，提交预约时会提示限制截止时间。
4. 按时到场签到会清零连续爽约次数并解除限制。
5. 爽约记录不影响已有预约的审批与签到。', 62, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    title = VALUES(title),
    keywords = VALUES(keywords),
    content = VALUES(content),
    priority = VALUES(priority),
    status = VALUES(status);

-- 校验：应返回 2 张新表
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('lab_closure', 'lab_waitlist');
