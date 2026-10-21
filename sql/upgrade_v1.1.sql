-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.0.0 -> v1.1.0
--
--  本脚本用于【已初始化过 v1.0.0 数据库】的在线升级，幂等可重复执行：
--   1. 新增 2 张表：lab_checkin（预约签到核销）、sys_notice（站内消息）
--   2. 新增 10 个权限点（签到核销 / 消息中心 / 实时看板）并绑定到各角色
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    java -cp mysql-connector-j.jar ImportSql.java <host> <user> <pwd> sql/upgrade_v1.1.sql
--
--  注意：lab_checkin / sys_notice 的实体继承 BaseEntity，
--       必须包含 deleted / create_time / update_time 三列，否则 MyBatis-Plus 逻辑删除会报 500。
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- ----------------------------------------------------------------------------
-- 1. 预约签到核销单表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_checkin (
    id              BIGINT       NOT NULL COMMENT '主键ID(雪花)',
    checkin_code    VARCHAR(16)  NOT NULL COMMENT '核销码(6位数字,全局唯一)',
    reservation_id  BIGINT       NOT NULL COMMENT '预约单ID',
    reservation_no  VARCHAR(64)  DEFAULT NULL COMMENT '预约单号(冗余快照)',
    lab_id          BIGINT       DEFAULT NULL COMMENT '实验室ID',
    lab_name        VARCHAR(128) DEFAULT NULL COMMENT '实验室名称(冗余快照)',
    user_id         BIGINT       NOT NULL COMMENT '使用人用户ID',
    user_name       VARCHAR(64)  DEFAULT NULL COMMENT '使用人姓名(冗余快照)',
    reserve_date    DATE         NOT NULL COMMENT '预约日期',
    start_time      TIME         NOT NULL COMMENT '预约开始时间',
    end_time        TIME         NOT NULL COMMENT '预约结束时间',
    status          VARCHAR(24)  NOT NULL DEFAULT 'SIGN_IN_PENDING'
        COMMENT '状态 SIGN_IN_PENDING待签到/CHECKED_IN已签到/COMPLETED已完成/EXPIRED已过期/CANCELLED已取消',
    checkin_time    DATETIME     DEFAULT NULL COMMENT '实际签到时间',
    checkout_time   DATETIME     DEFAULT NULL COMMENT '实际签退时间',
    checkin_by_id   BIGINT       DEFAULT NULL COMMENT '核销人用户ID',
    checkin_by_name VARCHAR(64)  DEFAULT NULL COMMENT '核销人姓名(冗余快照)',
    remark          VARCHAR(512) DEFAULT NULL COMMENT '核销备注',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkin_code (checkin_code),
    KEY idx_checkin_res (reservation_id),
    KEY idx_checkin_user (user_id),
    KEY idx_checkin_scope (reserve_date, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预约签到核销单表';

-- ----------------------------------------------------------------------------
-- 2. 站内消息表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notice (
    id          BIGINT        NOT NULL COMMENT '主键ID(雪花)',
    user_id     BIGINT        NOT NULL COMMENT '接收人用户ID',
    title       VARCHAR(128)  NOT NULL COMMENT '消息标题',
    content     VARCHAR(1024) DEFAULT NULL COMMENT '消息正文',
    notice_type VARCHAR(24)   NOT NULL DEFAULT 'SYSTEM' COMMENT '类型 SYSTEM系统/RESERVATION预约/REPAIR报修',
    biz_id      BIGINT        DEFAULT NULL COMMENT '关联业务ID(预约单/报修单等)',
    read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读 0未读 1已读',
    read_time   DATETIME      DEFAULT NULL COMMENT '已读时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_notice_user (user_id, read_flag),
    KEY idx_notice_type (notice_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息表';

-- ----------------------------------------------------------------------------
-- 3. 新增权限点（INSERT IGNORE 保证重复执行不报错）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (id, parent_id, perm_name, perm_code, perm_type, sort) VALUES
-- 签到核销
(1901, 0,    '签到核销',   'checkin',           1, 10),
(1902, 1901, '我的签到',   'checkin:mine',      2, 1),
(1903, 1901, '核销签到',   'checkin:verify',    2, 2),
(1904, 1901, '签到记录',   'checkin:list',      2, 3),
-- 消息中心
(2001, 0,    '消息中心',   'notice',            1, 11),
(2002, 2001, '我的消息',   'notice:mine',       2, 1),
(2003, 2001, '消息管理',   'notice:list',       2, 2),
(2004, 2001, '发送消息',   'notice:send',       2, 3),
-- 实验室实时看板
(2101, 0,    '实时看板',   'lab:board',         1, 12),
(2102, 2101, '看板查看',   'lab:board:view',    2, 1);

-- ----------------------------------------------------------------------------
-- 4. 角色授权（IGNORE 保证幂等；id 段 9xxx 与既有数据不冲突）
-- ----------------------------------------------------------------------------
-- 超级管理员：全部新增权限
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CONCAT(7000 + id), 1, id FROM sys_permission WHERE id BETWEEN 1901 AND 2199;

-- 实验室管理员：核销签到 + 签到记录 + 我的消息 + 消息管理/发送 + 看板
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9001, 2, 1901), (9002, 2, 1902), (9003, 2, 1903), (9004, 2, 1904),
(9005, 2, 2001), (9006, 2, 2002), (9007, 2, 2003), (9008, 2, 2004),
(9009, 2, 2101), (9010, 2, 2102);

-- 教师：核销签到 + 签到记录 + 我的消息 + 消息管理 + 看板
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9011, 3, 1901), (9012, 3, 1902), (9013, 3, 1903), (9014, 3, 1904),
(9015, 3, 2001), (9016, 3, 2002), (9017, 3, 2003),
(9018, 3, 2101), (9019, 3, 2102);

-- 学生：我的签到 + 我的消息 + 看板
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9021, 4, 1901), (9022, 4, 1902),
(9023, 4, 2001), (9024, 4, 2002),
(9025, 4, 2101), (9026, 4, 2102);

-- ----------------------------------------------------------------------------
-- 5. 将历史「已通过」预约补建签到凭证（若已存在则跳过，由业务层兜底亦可）
-- ----------------------------------------------------------------------------
-- 说明：核销码由后端生成，此处不直接插入，避免与业务层唯一性校验冲突；
--       读取旧数据时后端 codeOf() 会自动补建，无需手工处理。

-- 完成
