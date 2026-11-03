-- ============================================================================
-- v4.6 P1/P2 功能升级（Flyway 迁移脚本，同时可作为手工升级脚本执行）
--
-- 覆盖：
--   1. AI 知识库后台：新增 system:knowledge:manage 权限
--   2. 报修 SLA：优先级/指派/预计完成/超时提醒字段
--   3. 系统设置与公告：sys_config / sys_announcement
--   4. 数据权限：sys_user_lab（按实验室限定数据范围）
--   5. 通知外部渠道配置键（飞书/企业微信 webhook，存 sys_config）
--
-- 所有语句幂等：可重复执行，不覆盖既有业务数据。
-- ============================================================================

-- ---------- 1. AI 知识库后台权限 ----------
INSERT IGNORE INTO sys_permission (id, parent_id, perm_name, perm_code, perm_type, sort) VALUES
(2201, 0,    'AI知识库',   'system:knowledge',          1, 13),
(2202, 2201, '知识库管理', 'system:knowledge:manage',   2, 1),
-- 系统设置 / 系统公告
(2301, 0,    '系统设置',   'system:config',             1, 14),
(2302, 2301, '参数设置',   'system:config:manage',      2, 1),
(2303, 0,    '系统公告',   'system:announcement',       1, 15),
(2304, 2303, '公告管理',   'system:announcement:manage',2, 1);

-- 超级管理员（role 1）与实验室管理员（role 2）授予新权限
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CONCAT(94000 + p.id), 1, p.id FROM sys_permission p
WHERE p.perm_code IN ('system:knowledge', 'system:knowledge:manage',
                      'system:config', 'system:config:manage',
                      'system:announcement', 'system:announcement:manage');

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CONCAT(95000 + p.id), 2, p.id FROM sys_permission p
WHERE p.perm_code IN ('system:knowledge', 'system:knowledge:manage',
                      'system:config', 'system:config:manage',
                      'system:announcement', 'system:announcement:manage');

-- ---------- 2. 报修 SLA ----------
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，本脚本由 Flyway 保证只执行一次。
ALTER TABLE device_repair
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级 LOW/NORMAL/HIGH/URGENT' AFTER fault_desc,
    ADD COLUMN assignee_id BIGINT NULL COMMENT '指派处理人ID' AFTER handler_name,
    ADD COLUMN assignee_name VARCHAR(50) NULL COMMENT '指派处理人姓名' AFTER assignee_id,
    ADD COLUMN expected_finish_time DATETIME NULL COMMENT '预计完成时间（SLA 截止）' AFTER assignee_name,
    ADD COLUMN sla_notified TINYINT NOT NULL DEFAULT 0 COMMENT '超时提醒是否已推送 0否 1是' AFTER expected_finish_time;

CREATE INDEX idx_repair_priority ON device_repair (priority);
CREATE INDEX idx_repair_expected ON device_repair (expected_finish_time);

-- ---------- 3. 系统设置 ----------
CREATE TABLE IF NOT EXISTS sys_config (
    id          BIGINT       NOT NULL COMMENT '主键ID(雪花或固定ID)',
    config_key  VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value VARCHAR(1000) NULL COMMENT '配置值',
    value_type  VARCHAR(20)  NOT NULL DEFAULT 'STRING' COMMENT '值类型 STRING/NUMBER/BOOLEAN/JSON/SECRET',
    group_name  VARCHAR(50)  NOT NULL DEFAULT '基础设置' COMMENT '配置分组',
    label       VARCHAR(100) NULL COMMENT '展示名称',
    description VARCHAR(255) NULL COMMENT '说明',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    KEY idx_config_group (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置';

INSERT IGNORE INTO sys_config (id, config_key, config_value, value_type, group_name, label, description) VALUES
(40001, 'system.maintenance',        'false', 'BOOLEAN', '系统运行', '维护模式', '开启后仅管理员可访问业务接口，普通用户看到维护公告'),
(40002, 'system.maintenance.notice', '系统维护中，预计很快恢复，请稍后再试。', 'STRING', '系统运行', '维护提示文案', '维护模式开启时展示给普通用户的提示'),
(40003, 'system.announcement.banner','true',  'BOOLEAN', '系统运行', '显示公告横幅', '是否在页面顶部展示生效中的公告'),
(40004, 'schedule.auto-complete',    'true',  'BOOLEAN', '定时任务', '预约自动完成', '结束时间已过的已通过预约自动流转为已完成'),
(40005, 'schedule.remind',           'true',  'BOOLEAN', '定时任务', '开始前提醒', '预约开始前 30 分钟推送站内提醒'),
(40006, 'schedule.repair-sla',       'true',  'BOOLEAN', '定时任务', '报修超时提醒', '报修单超过 SLA 截止时间后提醒处理人'),
(40007, 'notify.webhook.feishu',     '',      'SECRET',  '外部通知', '飞书机器人 Webhook', '留空则不发送；配置后重要通知同步推送'),
(40008, 'notify.webhook.wecom',      '',      'SECRET',  '外部通知', '企业微信机器人 Webhook', '留空则不发送；配置后重要通知同步推送'),
(40009, 'notify.external.enabled',   'false', 'BOOLEAN', '外部通知', '启用外部通知', '开启后才向已配置的飞书/企业微信 webhook 推送');

-- ---------- 4. 系统公告 ----------
CREATE TABLE IF NOT EXISTS sys_announcement (
    id          BIGINT       NOT NULL COMMENT '主键ID(雪花)',
    title       VARCHAR(150) NOT NULL COMMENT '公告标题',
    content     TEXT         NOT NULL COMMENT '公告正文',
    level       VARCHAR(20)  NOT NULL DEFAULT 'INFO' COMMENT '级别 INFO/WARNING/URGENT',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0下线 1生效',
    start_time  DATETIME     NULL COMMENT '生效开始时间，为空表示立即',
    end_time    DATETIME     NULL COMMENT '生效结束时间，为空表示长期',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_announcement_status (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统公告';

-- ---------- 5. 数据权限（用户-实验室） ----------
CREATE TABLE IF NOT EXISTS sys_user_lab (
    id          BIGINT   NOT NULL COMMENT '主键ID(雪花)',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    lab_id      BIGINT   NOT NULL COMMENT '实验室ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_lab (user_id, lab_id),
    KEY idx_user_lab_user (user_id),
    KEY idx_user_lab_lab (lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户数据权限（可见实验室范围）';

-- ---------- 6. 历史归档表 ----------
-- 结构与原表完全一致，归档/回迁都不需要字段映射
CREATE TABLE IF NOT EXISTS lab_reservation_archive LIKE lab_reservation;
CREATE TABLE IF NOT EXISTS sys_oper_log_archive LIKE sys_oper_log;
