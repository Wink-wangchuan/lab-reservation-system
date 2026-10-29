-- ============================================================================
--  智慧实验室预约管理系统 - 数据库初始化脚本
--  版本: 1.0.0    目标数据库: MySQL 8.x
--  字符集: utf8mb4    存储引擎: InnoDB
--
--  说明:
--   1. 本脚本创建库 lab_reservation 及全部业务表 + 初始数据
--   2. 通过 MySQL 客户端执行:  mysql -uroot -p < mysql.sql
--   3. 初始账号(密码默认 123456, BCrypt 加密):
--        admin      超级管理员
--        labadmin   实验室管理员
--        teacher    教师
--        student    学生
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. 建库
-- ----------------------------------------------------------------------------
DROP DATABASE IF EXISTS lab_reservation;
CREATE DATABASE lab_reservation DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;
USE lab_reservation;

-- ============================================================================
-- 一、权限体系（RBAC）表
-- ============================================================================

-- 1.1 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL COMMENT '主键ID(雪花)',
    username      VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password      VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    real_name     VARCHAR(64)  NOT NULL COMMENT '真实姓名',
    gender        TINYINT      DEFAULT 0 COMMENT '性别 0男 1女 2保密',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar        VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用 2待审核(自助注册,需管理员审核)',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(管理端并发维护)',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_by     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    -- 手机号同时是验证码登录与找回密码的标识，必须唯一（唯一索引允许多个 NULL）
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

-- 1.2 角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL COMMENT '主键ID',
    role_code   VARCHAR(64) NOT NULL COMMENT '角色编码(如 TEACHER)',
    role_name   VARCHAR(64) NOT NULL COMMENT '角色名称(如 教师)',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(管理端并发维护)',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色表';

-- 1.3 权限/菜单表
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id          BIGINT       NOT NULL COMMENT '主键ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父级ID 0为顶级',
    perm_name   VARCHAR(64)  NOT NULL COMMENT '权限名称',
    perm_code   VARCHAR(128) NOT NULL COMMENT '权限标识(如 lab:reservation:submit)',
    perm_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '类型 1菜单 2按钮',
    path        VARCHAR(128) DEFAULT NULL COMMENT '路由地址',
    component   VARCHAR(128) DEFAULT NULL COMMENT '前端组件',
    icon        VARCHAR(64)  DEFAULT NULL COMMENT '图标',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(管理端并发维护)',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统权限表';

-- 1.4 用户-角色关联表
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id      BIGINT NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

-- 1.5 角色-权限关联表
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id            BIGINT NOT NULL COMMENT '主键ID',
    role_id       BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色权限关联表';

-- ============================================================================
-- 二、业务主表
-- ============================================================================

-- 2.1 实验室表
DROP TABLE IF EXISTS lab_info;
CREATE TABLE lab_info (
    id             BIGINT       NOT NULL COMMENT '主键ID',
    lab_code       VARCHAR(64)  NOT NULL COMMENT '实验室编号(如 LAB-A-101)',
    lab_name       VARCHAR(128) NOT NULL COMMENT '实验室名称',
    lab_type       VARCHAR(32)  NOT NULL COMMENT '实验室类型(计算机/物理/化学/电子)',
    location       VARCHAR(255) DEFAULT NULL COMMENT '所在位置',
    capacity       INT          NOT NULL DEFAULT 0 COMMENT '容量(座位数)',
    equipment_desc VARCHAR(1024) DEFAULT NULL COMMENT '设备配置说明',
    manager_id     BIGINT       DEFAULT NULL COMMENT '负责的实验室管理员ID',
    open_status    TINYINT      NOT NULL DEFAULT 1 COMMENT '预约开关 0关闭 1开放',
    enable_reserve TINYINT      NOT NULL DEFAULT 1 COMMENT '1可预约 0禁止预约(管控)',
    cover_url      VARCHAR(512) DEFAULT NULL COMMENT '封面图',
    remark         VARCHAR(512) DEFAULT NULL COMMENT '备注',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(管理端并发维护)',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_code (lab_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实验室表';

-- 2.2 开放时间段配置表（一周7天 × 若干时间段）
DROP TABLE IF EXISTS lab_time_slot;
CREATE TABLE lab_time_slot (
    id          BIGINT      NOT NULL COMMENT '主键ID',
    lab_id      BIGINT      NOT NULL COMMENT '实验室ID',
    week_day    TINYINT     NOT NULL COMMENT '星期 1-7(周一到周日)',
    slot_name   VARCHAR(64) NOT NULL COMMENT '时间段名称(如 上午第一节)',
    start_time  TIME        NOT NULL COMMENT '开始时间 08:00:00',
    end_time    TIME        NOT NULL COMMENT '结束时间 09:40:00',
    max_people  INT         NOT NULL DEFAULT 0 COMMENT '该时段可预约人数上限 0不限',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_slot_lab (lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实验室开放时间段表';

-- 2.3 预约单主表
--    状态机: DRAFT草稿 -> PENDING待审核 -> APPROVED已通过 -> COMPLETED已完成
--                                     -> REJECTED已驳回 | CANCELLED已取消
DROP TABLE IF EXISTS lab_reservation;
CREATE TABLE lab_reservation (
    id             BIGINT       NOT NULL COMMENT '主键ID',
    reservation_no VARCHAR(64)  NOT NULL COMMENT '预约单号',
    lab_id         BIGINT       NOT NULL COMMENT '实验室ID',
    lab_name       VARCHAR(128) DEFAULT NULL COMMENT '实验室名称(冗余快照)',
    student_id     BIGINT       NOT NULL COMMENT '发起学生用户ID',
    student_name   VARCHAR(64)  DEFAULT NULL COMMENT '学生姓名(冗余快照)',
    teacher_id     BIGINT       DEFAULT NULL COMMENT '审核教师用户ID',
    teacher_name   VARCHAR(64)  DEFAULT NULL COMMENT '教师姓名(冗余快照)',
    -- 预约时间（核心业务时间字段，Java 25 LocalDate / LocalTime）
    reserve_date   DATE         NOT NULL COMMENT '预约日期',
    start_time     TIME         NOT NULL COMMENT '开始时间',
    end_time       TIME         NOT NULL COMMENT '结束时间',
    purpose        VARCHAR(512) NOT NULL COMMENT '用途说明',
    people_count   INT          NOT NULL DEFAULT 1 COMMENT '使用人数',
    use_device_ids VARCHAR(512) DEFAULT NULL COMMENT '使用的设备ID集合(逗号分隔)',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PENDING/APPROVED/REJECTED/COMPLETED/CANCELLED',
    reject_reason  VARCHAR(512) DEFAULT NULL COMMENT '驳回原因',
    cancel_reason  VARCHAR(512) DEFAULT NULL COMMENT '取消原因',
    complete_time  DATETIME     DEFAULT NULL COMMENT '完成时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_no (reservation_no),
    KEY idx_reserve_scope (lab_id, reserve_date, start_time, end_time),
    KEY idx_reserve_date_status (reserve_date, status),
    KEY idx_reserve_student (student_id),
    KEY idx_reserve_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预约单表(含状态机)';

-- 2.4 预约审批记录表（多级审批流水）
DROP TABLE IF EXISTS lab_approval;
CREATE TABLE lab_approval (
    id             BIGINT       NOT NULL COMMENT '主键ID',
    reservation_id BIGINT       NOT NULL COMMENT '预约单ID',
    approval_level INT          NOT NULL DEFAULT 1 COMMENT '审批层级(当前预留多级)',
    approver_id    BIGINT       DEFAULT NULL COMMENT '审批人用户ID',
    approver_name  VARCHAR(64)  DEFAULT NULL COMMENT '审批人姓名(冗余快照)',
    action         VARCHAR(16)  NOT NULL COMMENT '动作 APPROVE通过 / REJECT驳回',
    comment        VARCHAR(512) DEFAULT NULL COMMENT '审批意见',
    approve_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    PRIMARY KEY (id),
    KEY idx_approval_res (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预约审批记录表';

-- 2.5 设备台账表
--    故障状态(fault_status)为 1(故障) 时，将锁定关联实验室不可预约
DROP TABLE IF EXISTS device;
CREATE TABLE device (
    id             BIGINT       NOT NULL COMMENT '主键ID',
    device_code    VARCHAR(64)  NOT NULL COMMENT '设备编号',
    device_name    VARCHAR(128) NOT NULL COMMENT '设备名称',
    device_type    VARCHAR(32)  DEFAULT NULL COMMENT '设备类型',
    lab_id         BIGINT       DEFAULT NULL COMMENT '所属实验室ID',
    lab_name       VARCHAR(128) DEFAULT NULL COMMENT '所属实验室名称(冗余快照)',
    purchase_date  DATE         DEFAULT NULL COMMENT '购置日期',
    brand_model    VARCHAR(128) DEFAULT NULL COMMENT '品牌型号',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '在用状态 0停用 1在用 2报废',
    fault_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '故障状态 0正常 1故障(故障期间锁定实验室)',
    fault_desc     VARCHAR(512) DEFAULT NULL COMMENT '当前故障描述',
    quantity       INT          NOT NULL DEFAULT 1 COMMENT '数量(该条台账代表的同类设备台/套数)',
    price          DECIMAL(12,2) DEFAULT NULL COMMENT '购置价格',
    remark         VARCHAR(512) DEFAULT NULL COMMENT '备注',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(管理端并发维护)',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (device_code),
    KEY idx_device_lab (lab_id),
    KEY idx_device_fault (fault_status, lab_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备台账表';

-- 2.6 设备报修单表（报修闭环）
DROP TABLE IF EXISTS device_repair;
CREATE TABLE device_repair (
    id            BIGINT       NOT NULL COMMENT '主键ID',
    repair_no     VARCHAR(64)  NOT NULL COMMENT '报修单号',
    device_id     BIGINT       NOT NULL COMMENT '设备ID',
    device_name   VARCHAR(128) DEFAULT NULL COMMENT '设备名称(冗余快照)',
    lab_id        BIGINT       DEFAULT NULL COMMENT '所属实验室ID',
    reporter_id   BIGINT       DEFAULT NULL COMMENT '报修人用户ID',
    reporter_name VARCHAR(64)  DEFAULT NULL COMMENT '报修人姓名',
    fault_desc    VARCHAR(1024) NOT NULL COMMENT '故障描述',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING待处理/REPAIRING维修中/COMPLETED已完成/CANCELLED已取消',
    handler_id    BIGINT       DEFAULT NULL COMMENT '处理人用户ID',
    handler_name  VARCHAR(64)  DEFAULT NULL COMMENT '处理人姓名',
    handle_remark VARCHAR(512) DEFAULT NULL COMMENT '处理意见',
    start_time    DATETIME     DEFAULT NULL COMMENT '维修开始时间',
    finish_time   DATETIME     DEFAULT NULL COMMENT '维修完成时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_repair_no (repair_no),
    KEY idx_repair_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备报修单表';

-- 2.7 操作日志表（AOP 全局记录）
DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
    id          BIGINT       NOT NULL COMMENT '主键ID',
    title       VARCHAR(128) DEFAULT NULL COMMENT '模块标题',
    business_type VARCHAR(32) DEFAULT NULL COMMENT '业务类型(INSERT/UPDATE/DELETE/QUERY)',
    method      VARCHAR(255) DEFAULT NULL COMMENT '请求方法',
    request_method VARCHAR(16) DEFAULT NULL COMMENT 'HTTP方法(GET/POST...)',
    oper_name   VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    oper_ip     VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
    oper_url    VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
    oper_param  TEXT         DEFAULT NULL COMMENT '请求参数(JSON)',
    json_result TEXT         DEFAULT NULL COMMENT '返回结果(截断)',
    error_msg   TEXT         DEFAULT NULL COMMENT '异常信息',
    cost_time   BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '操作状态 0失败 1成功',
    oper_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_oper_time (oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统操作日志表';

-- 2.8 预约签到核销单表（预约业务闭环：审批通过 -> 生成核销码 -> 签到 -> 签退完成）
DROP TABLE IF EXISTS lab_checkin;
CREATE TABLE lab_checkin (
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

-- 2.9 站内消息表（预约/报修等业务事件主动推送的个人消息）
DROP TABLE IF EXISTS sys_notice;
CREATE TABLE sys_notice (
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
    KEY idx_notice_user_time (user_id, create_time),
    KEY idx_notice_type (notice_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息表';

-- ============================================================================
-- 三、初始数据
-- ============================================================================

-- 3.1 初始角色 ---------------------------------------------------------------
-- role_code: SUPER_ADMIN / LAB_ADMIN / TEACHER / STUDENT
INSERT INTO sys_role (id, role_code, role_name, remark, sort) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '系统全部权限', 1),
(2, 'LAB_ADMIN',   '实验室管理员', '实验室与设备管理、审批', 2),
(3, 'TEACHER',     '教师',         '审批学生预约', 3),
(4, 'STUDENT',     '学生',         '提交与取消预约', 4);

-- 3.2 初始权限 ------------------------------------------------------------
-- 权限编码统一按 <资源>:<操作> 约定，用于 @PreAuthorize("hasAuthority('xxx')")
INSERT INTO sys_permission (id, parent_id, perm_name, perm_code, perm_type, sort) VALUES
-- 用户管理
(1001, 0, '用户管理', 'system:user', 1, 1),
(1002, 1001, '用户列表', 'system:user:list', 2, 1),
(1003, 1001, '新增用户', 'system:user:add', 2, 2),
(1004, 1001, '编辑用户', 'system:user:edit', 2, 3),
(1005, 1001, '删除用户', 'system:user:delete', 2, 4),
(1006, 1001, '分配角色', 'system:user:assignRole', 2, 5),
(1007, 1001, '重置密码', 'system:user:resetPwd', 2, 6),
-- 角色管理
(1101, 0, '角色管理', 'system:role', 1, 2),
(1102, 1101, '角色列表', 'system:role:list', 2, 1),
(1103, 1101, '新增角色', 'system:role:add', 2, 2),
(1104, 1101, '编辑角色', 'system:role:edit', 2, 3),
(1105, 1101, '删除角色', 'system:role:delete', 2, 4),
(1106, 1101, '分配权限', 'system:role:assignPerm', 2, 5),
-- 实验室管理
(1201, 0, '实验室管理', 'system:lab', 1, 3),
(1202, 1201, '实验室列表', 'system:lab:list', 2, 1),
(1203, 1201, '新增实验室', 'system:lab:add', 2, 2),
(1204, 1201, '编辑实验室', 'system:lab:edit', 2, 3),
(1205, 1201, '删除实验室', 'system:lab:delete', 2, 4),
(1206, 1201, '时间段配置', 'system:lab:slotConfig', 2, 5),
-- 设备台账管理
(1301, 0, '设备台账', 'system:device', 1, 4),
(1302, 1301, '设备列表', 'system:device:list', 2, 1),
(1303, 1301, '新增设备', 'system:device:add', 2, 2),
(1304, 1301, '编辑设备', 'system:device:edit', 2, 3),
(1305, 1301, '删除设备', 'system:device:delete', 2, 4),
-- 预约管理
(1401, 0, '预约管理', 'reservation:manage', 1, 5),
(1402, 1401, '提交预约', 'reservation:submit', 2, 1),
(1403, 1401, '我的预约', 'reservation:mine', 2, 2),
(1404, 1401, '取消预约', 'reservation:cancel', 2, 3),
(1405, 1401, '审批预约', 'reservation:approve', 2, 4),
(1406, 1401, '预约列表(管理)', 'reservation:list', 2, 5),
(1407, 1401, '导出预约', 'reservation:export', 2, 6),
-- 设备报修
(1501, 0, '设备报修', 'device:repair', 1, 6),
(1502, 1501, '提交报修', 'device:repair:submit', 2, 1),
(1503, 1501, '报修处理', 'device:repair:handle', 2, 2),
(1504, 1501, '报修查询', 'device:repair:list', 2, 3),
-- 数据统计
(1601, 0, '数据统计', 'statistics:view', 1, 7),
(1701, 0, '操作日志', 'system:operlog', 1, 8),
(1702, 1701, '日志查询', 'system:operlog:list', 2, 1),
-- 权限管理
(1801, 0, '权限管理', 'system:permission', 1, 9),
(1802, 1801, '新增权限', 'system:permission:add', 2, 1),
(1803, 1801, '编辑权限', 'system:permission:edit', 2, 2),
(1804, 1801, '删除权限', 'system:permission:delete', 2, 3),
-- 签到核销
(1901, 0, '签到核销', 'checkin', 1, 10),
(1902, 1901, '我的签到', 'checkin:mine', 2, 1),
(1903, 1901, '核销签到', 'checkin:verify', 2, 2),
(1904, 1901, '签到记录', 'checkin:list', 2, 3),
-- 消息中心
(2001, 0, '消息中心', 'notice', 1, 11),
(2002, 2001, '我的消息', 'notice:mine', 2, 1),
(2003, 2001, '消息管理', 'notice:list', 2, 2),
(2004, 2001, '发送消息', 'notice:send', 2, 3),
-- 实验室实时看板
(2101, 0, '实时看板', 'lab:board', 1, 12),
(2102, 2101, '看板查看', 'lab:board:view', 2, 1);

-- 3.3 初始用户（默认密码均为 123456，BCrypt 加密）-------------------------
-- BCrypt("123456") = $2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq
INSERT INTO sys_user (id, username, password, real_name, gender, phone, email, status) VALUES
(1, 'admin',     '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '系统管理员', 0, '13800000001', 'admin@lab.com', 1),
(2, 'labadmin',  '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '张管理',    0, '13800000002', 'labadmin@lab.com', 1),
(3, 'teacher',   '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '李教授',    0, '13800000003', 'teacher@lab.com', 1),
(4, 'teacher2',  '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '王教授',    0, '13800000004', 'teacher2@lab.com', 1),
(5, 'student',   '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '张三同学',   1, '13800000005', 'student@lab.com', 1),
(6, 'student2',  '$2a$10$3eEzSOMEIrpj4VGnoq.GRuLGQFxgz6AlPzVR8NmgIhJuDEs/6XEBq', '李四同学',   1, '13800000006', 'student2@lab.com', 1);

-- 3.4 用户-角色映射 --------------------------------------------------------
INSERT INTO sys_user_role (id, user_id, role_id) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 3),
(5, 5, 4),
(6, 6, 4);

-- 3.5 角色-权限映射（超级管理员绑全部权限；其余角色按业务需要）--------------
-- 超级管理员(role 1): 全部权限
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT CONCAT(6000 + id), 1, id FROM sys_permission;
-- 实验室管理员(role 2): 实验室管理+时间段+设备台账+报修处理+预约审批+统计+日志+签到核销+消息中心+实时看板
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(2001, 2, 1202),(2002, 2, 1203),(2003, 2, 1204),(2004, 2, 1206),
(2005, 2, 1302),(2006, 2, 1303),(2007, 2, 1304),(2008, 2, 1305),
(2009, 2, 1501),(2010, 2, 1503),(2011, 2, 1504),
(2012, 2, 1401),(2013, 2, 1405),(2014, 2, 1406),(2015, 2, 1407),
(2016, 2, 1601),(2017, 2, 1701),(2018, 2, 1702),
(9001, 2, 1901),(9002, 2, 1902),(9003, 2, 1903),(9004, 2, 1904),
(9005, 2, 2001),(9006, 2, 2002),(9007, 2, 2003),(9008, 2, 2004),
(9009, 2, 2101),(9010, 2, 2102);
-- 教师(role 3): 审批预约、查看统计、核销签到、消息中心（含发送消息）、可自行预约实验室、
--                可提交报修并查看报修工单（维修处理仍归实验室管理员）
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(3001, 3, 1401),(3002, 3, 1405),(3003, 3, 1406),
(9103, 3, 1402),(9104, 3, 1403),(9105, 3, 1404),
(3004, 3, 1601),
(9011, 3, 1901),(9012, 3, 1902),(9013, 3, 1903),(9014, 3, 1904),
(9015, 3, 2001),(9016, 3, 2002),(9017, 3, 2003),(9106, 3, 2004),
(9018, 3, 2101),(9019, 3, 2102),
(9201, 3, 1502),(9202, 3, 1504);
-- 学生(role 4): 提交预约、我的预约、取消预约、提交报修、我的签到、我的消息、实时看板、数据驾驶舱
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(4001, 4, 1401),(4002, 4, 1402),(4003, 4, 1403),(4004, 4, 1404),
(4005, 4, 1501),(4006, 4, 1502),
(9021, 4, 1901),(9022, 4, 1902),
(9023, 4, 2001),(9024, 4, 2002),
(9025, 4, 2101),(9026, 4, 2102),
-- 数据驾驶舱面向所有登录用户可见（学生看校园实验室运行概览）
(9027, 4, 1601);

-- 3.6 初始实验室 ------------------------------------------------------------
-- 注：lab_type 取值必须与前端「实验室类型」下拉保持一致，否则管理端选不中/筛选不到
INSERT INTO lab_info (id, lab_code, lab_name, lab_type, location, capacity, equipment_desc, manager_id, open_status, enable_reserve) VALUES
(101, 'LAB-A-101', '第一计算机实验室',  '计算机实验室',   '实验楼 A 栋 101', 60, 'i7 高性能主机 60 台', 2, 1, 1),
(102, 'LAB-A-201', '人工智能创新实验室', '人工智能实验室', '实验楼 A 栋 201', 40, 'RTX 显卡工作站 40 台', 2, 1, 1),
(103, 'LAB-B-102', '大学物理实验室',    '物理实验室',     '实验楼 B 栋 102', 50, '光学/力学实验台 25 套', 2, 1, 1),
(104, 'LAB-B-203', '电子技术实验室',    '电子实训室',     '实验楼 B 栋 203', 45, '示波器/万用表 40 套', 2, 1, 1);

-- 3.7 开放时段：统一按学校一般作息生成（周一至周五 × 上午两节 + 下午两节），
--     覆盖全部实验室，生成逻辑见 §3.9（需先插入全部实验室再生成）


-- 3.8 初始设备台账（按「实验室设备配置」登记，与 sql/upgrade_v1.9.sql 一致）-----
-- 一条台账 = 一间实验室的一类设备配置；设备编号字段标注其覆盖的资产编号区间；
-- quantity 为该配置的台/套数，驾驶舱「设备总数」按其汇总得到实际台数。
INSERT INTO device
(id, device_code, device_name, device_type, lab_id, lab_name, purchase_date, brand_model, status, fault_status, quantity, price, remark)
VALUES
-- 101 第一计算机实验室
(10101, 'DEV-101-001 ~ DEV-101-060', '教学台式机',        '计算机',     101, '第一计算机实验室',   '2024-03-12', '联想 ThinkCentre M760',  1, 0, 60, 5200.00,  '共 60 台'),
(10102, 'DEV-101-061 ~ DEV-101-062', '高性能图形工作站',  '计算机',     101, '第一计算机实验室',   '2023-03-15', 'Dell Precision 7865',    1, 0,  2, 35000.00, '共 2 台'),
(10103, 'DEV-101-063',               '千兆以太网交换机',  '网络设备',   101, '第一计算机实验室',   '2022-06-01', 'H3C S5130',              1, 0,  1, 12800.00, '共 1 台'),
-- 102 人工智能创新实验室
(10201, 'DEV-102-001 ~ DEV-102-040', 'AI 训练工作站',     '计算机',     102, '人工智能创新实验室', '2025-03-20', 'RTX 4090 图形工作站',    1, 0, 40, 42000.00, '共 40 台'),
(10202, 'DEV-102-041',               '深度学习服务器',    '服务器',     102, '人工智能创新实验室', '2023-09-20', 'NVIDIA DGX A100',        1, 0,  1, 320000.00,'共 1 台'),
-- 103 大学物理实验室
(10301, 'DEV-103-001 ~ DEV-103-025', '物理实验台',        '实验仪器',   103, '大学物理实验室',     '2023-09-01', '国产定制实验台',         1, 0, 25, 8800.00,  '共 25 台'),
(10302, 'DEV-103-026',               '光学实验台',        '实验仪器',   103, '大学物理实验室',     '2021-05-10', '国产定制光学实验台',     1, 0,  1, 8800.00,  '共 1 台'),
-- 104 电子技术实验室
(10401, 'DEV-104-001 ~ DEV-104-020', '数字示波器',        '实验仪器',   104, '电子技术实验室',     '2022-11-03', 'Tektronix TBS1104',      1, 0, 20, 6500.00,  '共 20 台'),
(10402, 'DEV-104-021 ~ DEV-104-045', '数字万用表',        '实验仪器',   104, '电子技术实验室',     '2023-05-10', 'Fluke 17B+',             1, 0, 25, 680.00,   '共 25 台'),
-- 105 第二计算机实验室
(10501, 'DEV-105-001 ~ DEV-105-060', '教学台式机',        '计算机',     105, '第二计算机实验室',   '2024-03-12', '联想 ThinkCentre M760',  1, 0, 60, 5200.00,  '共 60 台'),
(10502, 'DEV-105-061',               '高清投影仪',        '显示设备',   105, '第二计算机实验室',   '2024-03-12', '爱普生 CB-X06',          1, 0,  1, 3800.00,  '共 1 台'),
-- 106 云计算与大数据机房
(10601, 'DEV-106-001 ~ DEV-106-004', 'GPU 计算节点',      '服务器',     106, '云计算与大数据机房', '2024-09-01', 'NVIDIA A100 计算节点',   1, 0,  4, 268000.00,'共 4 台'),
(10602, 'DEV-106-005',               '分布式存储服务器',  '存储设备',   106, '云计算与大数据机房', '2024-09-01', '群晖 RS3621xs+',         1, 0,  1, 46000.00, '共 1 台'),
(10603, 'DEV-106-006 ~ DEV-106-013', '服务器机柜',        '实验仪器',   106, '云计算与大数据机房', '2024-09-01', '图腾 42U 标准机柜',      1, 0,  8, 4800.00,  '共 8 个'),
-- 107 软件工程实训室
(10701, 'DEV-107-001 ~ DEV-107-050', '双屏开发工作站',    '计算机',     107, '软件工程实训室',     '2024-04-18', 'Dell Precision 3660',    1, 0, 50, 9800.00,  '共 50 台'),
-- 108 网络技术实训室
(10801, 'DEV-108-001 ~ DEV-108-020', '路由交换实验台',    '网络设备',   108, '网络技术实训室',     '2023-11-06', '华为 S5731 实验台',      1, 0, 20, 15600.00, '共 20 组'),
(10802, 'DEV-108-021',               '核心三层交换机',    '网络设备',   108, '网络技术实训室',     '2023-11-06', '华为 S5731-H48T4XC',     1, 0,  1, 15600.00, '共 1 台'),
(10803, 'DEV-108-022 ~ DEV-108-027', '网络实训机柜',      '实验仪器',   108, '网络技术实训室',     '2023-11-06', '图腾 42U 标准机柜',      1, 0,  6, 2600.00,  '共 6 个'),
(10804, 'DEV-108-028',               '企业级路由器',      '网络设备',   108, '网络技术实训室',     '2023-11-06', '华为 AR6300',            1, 0,  1, 12800.00, '共 1 台'),
-- 109 人工智能训练室
(10901, 'DEV-109-001 ~ DEV-109-040', 'AI 训练工作站',     '服务器',     109, '人工智能训练室',     '2025-03-20', 'RTX 4090 图形工作站',    1, 0, 40, 62000.00, '共 40 台'),
(10902, 'DEV-109-041 ~ DEV-109-050', '边缘推理盒子',      '物联网设备', 109, '人工智能训练室',     '2025-03-20', 'Jetson Orin NX 套件',    1, 0, 10, 4200.00,  '共 10 台'),
-- 110 语音实训室
(11001, 'DEV-110-001 ~ DEV-110-048', '语音学习终端',      '计算机',     110, '语音实训室',         '2023-06-15', '讯飞语音学习终端',       1, 0, 48, 3600.00,  '共 48 位'),
(11002, 'DEV-110-049',               '同声传译控制台',    '实验仪器',   110, '语音实训室',         '2023-06-15', 'JTS-200 同传系统',       1, 0,  1, 32000.00, '共 1 套'),
-- 111 嵌入式与物联网实训室
(11101, 'DEV-111-001 ~ DEV-111-040', 'STM32 开发套件',    '实训器械',   111, '嵌入式与物联网实训室', '2024-05-08', 'STM32F407 开发板',     1, 0, 40, 680.00,   '共 40 套'),
(11102, 'DEV-111-041 ~ DEV-111-060', '传感器实验箱',      '实训器械',   111, '嵌入式与物联网实训室', '2024-05-08', '物联网传感器实验箱',   1, 0, 20, 2600.00,  '共 20 个'),
-- 112 电子工艺实训室
(11201, 'DEV-112-001 ~ DEV-112-045', '焊接工位',          '实训器械',   112, '电子工艺实训室',     '2023-10-11', '白光 FX-888D 焊接工位',  1, 0, 45, 1200.00,  '共 45 个'),
(11202, 'DEV-112-046 ~ DEV-112-065', '数字示波器',        '实验仪器',   112, '电子工艺实训室',     '2023-10-11', 'Tektronix TBS1104',      1, 0, 20, 6500.00,  '共 20 台'),
(11203, 'DEV-112-066 ~ DEV-112-070', '热风返修台',        '实训器械',   112, '电子工艺实训室',     '2023-10-11', '快克 857D',              1, 0,  5, 980.00,   '共 5 台'),
-- 113 电气控制训练室
(11301, 'DEV-113-001 ~ DEV-113-018', 'PLC 综合实训台',    '实训器械',   113, '电气控制训练室',     '2024-01-09', '西门子 S7-1200 实训台',  1, 0, 18, 26000.00, '共 18 套'),
(11302, 'DEV-113-019 ~ DEV-113-036', '变频器实训模块',    '实训器械',   113, '电气控制训练室',     '2024-01-09', '西门子 G120 变频模块',   1, 0, 18, 8600.00,  '共 18 套'),
(11303, 'DEV-113-037 ~ DEV-113-054', '伺服电机实训套件',  '实训器械',   113, '电气控制训练室',     '2024-01-09', '松下伺服电机套件',       1, 0, 18, 5400.00,  '共 18 套'),
-- 114 化学分析实验室
(11401, 'DEV-114-001 ~ DEV-114-020', '电子分析天平',      '实验仪器',   114, '化学分析实验室',     '2022-09-05', '赛多利斯 BSA224S',       1, 0, 20, 12800.00, '共 20 台'),
(11402, 'DEV-114-021 ~ DEV-114-032', '化学通风橱',        '实验仪器',   114, '化学分析实验室',     '2022-09-05', '定制全钢通风橱',         1, 0, 12, 9800.00,  '共 12 个'),
-- 115 智能制造训练中心
(11501, 'DEV-115-001 ~ DEV-115-006', '六轴工业机器人',    '实训器械',   115, '智能制造训练中心',   '2025-04-02', '发那科 LR Mate 200iD',   1, 0,  6, 168000.00,'共 6 台'),
(11502, 'DEV-115-007 ~ DEV-115-026', '数字孪生工作站',    '计算机',     115, '智能制造训练中心',   '2025-04-02', '联想 P3 图形工作站',     1, 0, 20, 24000.00, '共 20 台'),
(11503, 'DEV-115-027 ~ DEV-115-030', '机器视觉检测台',    '实训器械',   115, '智能制造训练中心',   '2025-04-02', '海康 Vision 检测台',     1, 0,  4, 36000.00, '共 4 台'),
-- 116 生物标本实验室
(11601, 'DEV-116-001 ~ DEV-116-030', '双目生物显微镜',    '实验仪器',   116, '生物标本实验室',     '2023-03-28', '奥林巴斯 CX23',          1, 0, 30, 6800.00,  '共 30 台'),
(11602, 'DEV-116-031 ~ DEV-116-036', '恒温培养箱',        '实验仪器',   116, '生物标本实验室',     '2023-03-28', '上海一恒 BPX-82',        1, 0,  6, 7200.00,  '共 6 台');

-- ============================================================================
-- 3.9 新增实训场地（机房 / 实训室 / 训练室）
--     与 sql/upgrade_v1.5.sql 保持一致，保证全新建库与在线升级结果相同
-- ============================================================================
INSERT INTO lab_info (id, lab_code, lab_name, lab_type, location, capacity, equipment_desc, manager_id, open_status, enable_reserve) VALUES
(105, 'LAB-A-102', '第二计算机实验室',     '计算机实验室',   '实验楼 A 栋 102', 60, '联想教学台式机 60 台、投影 1 套', 2, 1, 1),
(106, 'LAB-A-202', '云计算与大数据机房',   '机房',           '实验楼 A 栋 202', 45, '服务器机柜 8 个、GPU 计算节点 4 台', 2, 1, 1),
(107, 'LAB-A-303', '软件工程实训室',       '软件实训室',     '实验楼 A 栋 303', 50, '双屏开发工作站 50 套', 2, 1, 1),
(108, 'LAB-A-305', '网络技术实训室',       '网络实训室',     '实验楼 A 栋 305', 40, '路由交换实验台 20 组、标准机柜 6 个', 2, 1, 1),
(109, 'LAB-A-401', '人工智能训练室',       '人工智能实验室', '实验楼 A 栋 401', 40, 'AI 训练工作站 40 台、边缘推理盒 10 台', 2, 1, 1),
(110, 'LAB-A-205', '语音实训室',           '语音实训室',     '实验楼 A 栋 205', 48, '语音终端 48 位、同声传译系统 1 套', 2, 1, 1),
(111, 'LAB-B-201', '嵌入式与物联网实训室', '电子实训室',     '实验楼 B 栋 201', 40, 'STM32 开发套件 40 套、传感器实验箱 20 套', 2, 1, 1),
(112, 'LAB-B-205', '电子工艺实训室',       '电子实训室',     '实验楼 B 栋 205', 45, '焊接工位 45 个、数字示波器 20 台', 2, 1, 1),
(113, 'LAB-B-301', '电气控制训练室',       '电气实训室',     '实验楼 B 栋 301', 36, 'PLC 综合实训台 18 套、变频器模块 18 套', 2, 1, 1),
(114, 'LAB-C-101', '化学分析实验室',       '化学实验室',     '实验楼 C 栋 101', 40, '通风橱 12 个、分析天平 20 台', 2, 1, 1),
(115, 'LAB-D-101', '智能制造训练中心',     '智能制造训练室', '实训楼 D 栋 101', 40, '六轴工业机器人 6 台、数字孪生工作站 20 台', 2, 1, 1),
(116, 'LAB-C-203', '生物标本实验室',       '生物实验室',     '实验楼 C 栋 203', 36, '双目生物显微镜 30 台、恒温培养箱 6 台', 2, 1, 1);
-- 开放时段：统一按学校一般作息生成（覆盖全部 16 个实验室）
-- 周一至周五：每天 4 节   上午第一节 08:00-09:40、上午第二节 10:00-11:40、
--                         下午第一节 14:00-15:40、下午第二节 16:00-17:40
-- 周六      ：全天 4 节（同上）
-- 周日      ：仅上午 2 节（下午休息）
-- 主键 = 实验室ID*1000 + 星期*10 + 节次，共 16 × (5×4 + 4 + 2) = 416 条
INSERT INTO lab_time_slot
(id, lab_id, week_day, slot_name, start_time, end_time, max_people, status, deleted, create_time, update_time)
SELECT l.id * 1000 + d.wd * 10 + s.n,
       l.id, d.wd, s.slot_name, s.st_time, s.en_time, l.capacity, 1, 0, NOW(), NOW()
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

-- ============================================================================
-- 3.10 AI 助手知识库
--      与 sql/upgrade_v2.2.sql 保持一致，保证全新建库与在线升级结果相同
-- ============================================================================
CREATE TABLE ai_knowledge (
    id          BIGINT        NOT NULL COMMENT '主键ID(雪花)',
    category    VARCHAR(50)   NOT NULL COMMENT '知识分类',
    title       VARCHAR(100)  NOT NULL COMMENT '知识标题',
    keywords    VARCHAR(500)  NOT NULL COMMENT '触发关键词(逗号分隔,命中任意即计分)',
    content     TEXT          NOT NULL COMMENT '知识正文(命中后注入AI提示词)',
    priority    INT           NOT NULL DEFAULT 0 COMMENT '命中排序优先级(同分时越大越靠前)',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_knowledge_status (status),
    KEY idx_knowledge_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手知识库';

INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES
(30001, '系统概览', '系统功能总览与页面导航',
 '系统,功能,介绍,怎么用,如何使用,有什么用,帮助,导航,页面,模块,能做什么,平台,这个系统',
 '「智慧实验室预约管理系统」面向高校实验室的预约与运行管理，覆盖从提交预约、审批、签到核销到设备报修的完整闭环。
功能模块与页面对应关系：
1. 数据驾驶舱：实验室/设备/用户总量、预约总次数、今日预约、待审核、待处理报修、故障设备数，以及热门实验室、预约状态分布、近7日趋势、实验室类型分布。
2. 预约申请：提交实验室预约（学生与教师均可提交）。
3. 预约管理：查询与审批预约单、导出 Excel。
4. 实验室管理：实验室台账（编号、名称、类型、位置、容量、设备说明、开放状态、是否可预约）。
5. 开放时段配置：按星期配置每间实验室的可预约时段。
6. 实时占用看板：全部实验室当前占用状态（空闲/使用中/已预约/故障锁定/未开放）。
7. 设备台账：设备登记与状态管理。
8. 报修闭环：提交报修、受理维修、完成闭环。
9. 我的签到：查看本人核销码与签到记录。
10. 签到核销：教师/管理员输入核销码完成签到核销。
11. 消息中心：站内消息通知。
12. 个人中心：维护个人资料与密码。
13. 系统管理（仅管理员）：用户、角色、权限、操作日志。', 90, 1),
(30002, '角色权限', '四种角色的权限区别',
 '角色,权限,区别,能做什么,学生,教师,老师,管理员,超级管理员,实验室管理员,身份,谁可以',
 '系统共 4 种角色：
1. 学生：提交/取消预约、查看我的预约与核销码、到场出示核销码签到、签退、提交设备报修、接收站内消息。
2. 教师：在学生全部能力之上，可审批学生提交的预约、在「签到核销」页输入核销码为学生核销签到（看不到核销码本身）、向学生/教师/实验室发送站内消息、提交实验室预约（教师提交的预约只有管理员能审批）、提交设备报修并查看报修工单（维修处理仍由实验室管理员负责）。
3. 实验室管理员：管理实验室台账与开放时段、管理设备台账、处理报修、审批预约（含教师提交的预约）、查看全部预约与签到记录。
4. 超级管理员：拥有系统全部权限，额外包括用户管理（注册审核、重置密码、分配角色）、角色与权限管理、操作日志查询、查看所有核销码（兜底处理异常签到）。', 80, 1),
(30003, '预约流程', '预约申请的操作步骤与填写要求',
 '预约,申请,提交,怎么约,如何预约,预约申请,约实验室,填写,下单,想预约',
 '预约入口：「预约申请」页（左侧菜单）。步骤：
1. 选择实验室（仅显示开放且允许预约的实验室）。
2. 选择预约日期（不能早于今天）与开始/结束时间（结束必须晚于开始，且必须完整落在该实验室当天的某个开放时段内）。
3. 填写用途说明与使用人数（不能超过实验室容量）。
4. 可选择要使用的设备（设备须属于该实验室且无故障）。
5. 提交后预约单进入「待审核」状态，单号以 RSV 开头，提交人和审批人都会收到站内消息。
常见拦截原因：时间冲突（该时段已被有效预约占用）、实验室存在故障设备被锁定、人数超容量、时段不在开放时间内。', 70, 1),
(30004, '预约流程', '预约单的六种状态与流转规则',
 '预约状态,待审核,已通过,已驳回,已完成,已取消,草稿,状态流转,预约的状态',
 '预约单共 6 种状态：草稿、待审核、已通过、已完成、已驳回、已取消。
流转规则：
- 待审核：审批人可通过（→已通过）或驳回（→已驳回，附驳回原因）。
- 已通过：到场签到并签退后自动变已完成；开始前也可取消。
- 草稿/已取消/已驳回的预约不占用时段，不阻塞他人预约。
- 已驳回/已完成/已取消为终态，不可再流转。', 40, 1),
(30005, '审批规则', '预约审批规则：谁来审、怎么审',
 '审批,审核,批准,通过,驳回,谁审,审批人,审批规则,待办,怎么审批',
 '审批入口：「预约管理」页（需具备审批权限）。
核心规则：
1. 学生提交的预约：教师、实验室管理员、超级管理员均可审批。
2. 教师提交的预约：只有超级管理员或实验室管理员能审批，其他教师无权批准。
3. 任何人不能审批自己提交的预约（职责分离）。
4. 仅「待审核」状态可审批；驳回时填写驳回原因，提交人会收到带原因的站内消息。
5. 审批通过会自动生成 6 位签到核销码并通知预约人，同时记录一条审批流水（审批人、动作、意见、时间）。
6. 审批通过前系统会复核设备故障状态，若审批期间设备突发故障，该单无法通过。', 70, 1),
(30006, '预约流程', '取消预约的规则',
 '取消,撤销,退约,取消预约,不想去了,撤回',
 '取消入口：「我的预约」列表。
规则：
1. 只能取消本人提交的预约。
2. 仅「待审核」和「已通过」状态允许取消；已驳回/已完成/已取消为终态。
3. 时间边界：已通过的预约只能在开始时间之前取消；一旦到达预约开始时间，系统会直接拒绝并提示「预约已开始或已结束，无法取消」。
4. 取消需填写原因；取消后对应的签到核销凭证同步作废。
5. 撤回待审核的预约时，审批人会收到「无需再审批」的提醒，避免无效待办。', 75, 1),
(30007, '签到核销', '签到核销流程与核销码规则',
 '签到,核销,核销码,签到码,签退,怎么签到,核销台,凭证,验证码,到场',
 '签到闭环：预约审批通过 → 系统自动生成 6 位数字核销码 → 到场出示 → 核销签到 → 使用结束签退 → 预约完成。
要点：
1. 核销码在「我的签到」页查看，仅预约本人与超级管理员可见；教师等核销人看不到核销码，只能现场输入核销，防止提前泄露。
2. 签到时间窗：预约开始前 30 分钟至结束时刻之间才能核销签到，过早会被拒绝。
3. 谁来核销：教师和管理员在「签到核销」页输入学生出示的核销码完成签到。
4. 签退：本人或核销人在「我的签到」操作，签退后预约单自动置为「已完成」。
5. 超时未签到：系统每 5 分钟自动扫描，超过结束时刻仍未签到的凭证置为「已过期」并通知预约人；已签到但未签退的自动完成。', 70, 1),
(30008, '签到核销', '签到凭证的五种状态',
 '签到状态,待签到,已签到,已过期,签到记录,签到的情况',
 '签到凭证共 5 种状态：待签到、已签到、已完成、已过期、已取消。
流转规则：
- 待签到（审批通过已生成核销码）→ 已签到（核销成功）→ 已完成（签退或超时自动完成）。
- 已过期：超过预约结束时刻仍未签到，由定时任务自动置为过期并通知。
- 已取消：预约被取消时签到凭证同步取消。', 30, 1),
(30009, '开放时段', '开放时段配置与预约时段校验规则',
 '时段,开放时间,时间段,几点,时间安排,开放时段,星期,周几,节次,上课时间',
 '每间实验室按星期（周一至周日）配置若干开放时段，每个时段包含名称、开始/结束时间与该时段可预约人数上限。
预约校验规则：申请的预约时段必须被该实验室当天（按预约日期对应的星期）某个启用中的时段完整包含，跨时段或超出时段边界的预约会被拒绝。
默认作息（以实际配置为准）：周一至周六每天 4 节——上午第一节 08:00-09:40、上午第二节 10:00-11:40、下午第一节 14:00-15:40、下午第二节 16:00-17:40；周日仅上午 2 节。
时段由管理员在「开放时段配置」页维护，可按实验室和星期筛选、启停。', 60, 1),
(30010, '统计看板', '实时占用看板的五种状态说明',
 '看板,占用,空闲,使用中,已预约,锁定,未开放,实时,实验室状态,占用情况',
 '「实时占用看板」展示每间实验室的当前状态，共 5 种：
- 空闲：当前无有效占用，可预约。
- 使用中：当前时刻落在某条已通过预约的时段内。
- 已预约：存在尚未开始的今日预约。
- 故障锁定：实验室存在故障设备，暂停预约。
- 未开放：实验室开放开关关闭。
卡片同时展示当前使用人与时段、今日预约场次、未来日期的预约提示；顶部汇总条给出各状态数量与实时使用率。看板数据约 20 秒刷新一次缓存。
注意：看板的「空闲」指当天当前时刻空闲；其他日期的已通过预约不影响今天的空闲判定，但提交新预约时仍会做时间冲突校验。', 50, 1),
(30011, '设备报修', '设备台账与故障锁定机制',
 '设备,台账,设备管理,设备状态,停用,报废,故障,锁定,器材,仪器',
 '「设备台账」登记每间实验室的设备：编号、名称、类型、所属实验室、购置日期、品牌型号、数量（一行代表多台/套）、单价、运行状态（停用/在用/报废）与故障状态。
故障锁定机制：只要实验室下存在任意一台故障设备，整间实验室立即锁定、禁止新预约，直至故障解除；预约时指定使用的设备也会校验是否属于该实验室且无故障。
设备状态由管理员在「设备台账」页维护。', 50, 1),
(30012, '设备报修', '设备报修的完整流程',
 '报修,维修,坏了,修理,报修单,受理,故障申报,损坏,修设备',
 '设备损坏时的处理流程（「报修闭环」页）：
1. 任何登录用户均可提交报修：选择故障设备、填写故障描述，生成报修单，状态「待处理」。
2. 提交后报修人收到确认消息，具备处理权限的管理员/实验室管理员收到待办提醒。
3. 受理后进入「维修中」，报修人收到受理通知。
4. 维修完成填写处理意见后置为「已完成」，报修人收到完成通知；也可取消报修。
5. 报修期间该设备所在实验室会被锁定，暂停预约，防止带病使用。', 60, 1),
(30013, '消息通知', '站内消息的全部通知场景',
 '消息,通知,提醒,站内信,未读,消息中心,站内消息,收不到消息',
 '「消息中心」接收全流程站内通知，主要场景：
- 预约类：预约已提交待审核、新的预约待审批（发给审批人）、审核通过、审核驳回（附原因）、预约已取消、待审核预约被撤回、预约已过期（超时未签到）。
- 报修类：报修已提交、新的报修待处理（发给处理人）、报修受理维修中、报修已完成、报修已取消。
- 账号类：新用户注册待审核（发给审核人）、账号审核通过、登录密码被重置、账号角色已更新。
- 教师还可在消息中心主动向学生/教师/实验室发送消息。
消息分系统/预约/报修三类，支持已读标记。', 50, 1),
(30014, '统计看板', '数据驾驶舱的统计内容',
 '统计,驾驶舱,概览,热门,趋势,分布,数据统计,汇总,总量,数据',
 '「数据驾驶舱」提供全局统计：
- 概览卡片：实验室总数、设备总数（按数量汇总）、用户总数、预约总次数、今日预约数、待审核预约数、待处理报修数、故障设备数。
- 热门实验室排行：按预约热度排名。
- 预约状态分布、近 7 日预约趋势、实验室类型分布图表。
- 预约记录可在「预约管理」页导出为 Excel 文件。
统计结果带 Redis 缓存，预约/报修等写操作后自动失效重算。', 50, 1),
(30015, '常见问题', '账号注册、审核与密码',
 '注册,审核,账号,登录,密码,找回,重置,忘记密码,新用户,注册审核',
 '账号说明：
1. 新用户在登录页「注册账号」，提交后进入待审核状态，由管理员在「用户管理」页审核通过后方可登录。
2. 忘记密码：登录页「找回密码」按流程找回。
3. 管理员可在「用户管理」重置用户密码（用户会收到站内通知并需重新登录）。
4. 用户角色由管理员分配，角色变更会发送通知。
5. 个人资料（姓名、性别、手机、邮箱、头像地址）与修改密码在「个人中心」维护。', 40, 1),
(30016, '常见问题', '常见问题与故障排查 FAQ',
 '为什么,怎么办,失败,报错,不行,无法,冲突,驳回,过期,约不了,约不上,FAQ,常见问题,解决',
 '常见问题：
1. 预约提交失败提示时间冲突：该实验室该时段已被有效预约占用（草稿/已取消/已驳回不占用），请换时段或换实验室。
2. 提示实验室被锁定：该实验室存在故障设备，需等报修完成。
3. 提示不在开放时段内：预约时段必须完整落在当天某个开放时段里，请调整时间。
4. 预约被驳回：在「我的预约」查看驳回原因，调整后重新提交。
5. 核销码看不到：核销码仅本人与超级管理员可见，教师核销时请学生现场出示。
6. 签到提示未到时间：开始前 30 分钟才允许签到。
7. 预约过期：超过结束时刻未签到会自动过期，需重新预约。
8. 导出 Excel：在「预约管理」页点击导出按钮，按当前筛选条件导出。
9. 头像不显示：在「个人中心-资料设置」填入可直接访问的图片 URL。', 60, 1),
(30017, '常见问题', '个人中心：资料设置与修改密码',
 '个人中心,资料,头像,修改密码,个人信息,改密码,改资料',
 '「个人中心」两个页签：
1. 资料设置：维护真实姓名、性别、手机号、邮箱与头像地址（填可直接访问的图片 URL，保存后全站头像同步更新，包括 AI 助手对话页）。
2. 修改密码：输入原密码与新密码，修改成功后需用新密码重新登录。', 30, 1),
(30018, '常见问题', 'AI 助手的能力边界',
 'AI,助手,你能,你会,人工智能,机器人,智能问答,你可以',
 '我是系统内置 AI 助手，能力包括：
1. 查询实时数据：实验室实时占用、统计概览、热门实验室、我的预约、我的签到、故障设备、我的报修、未读消息。
2. 解答业务知识：预约流程、审批规则、签到核销、开放时段、报修流程、角色权限等（来自系统知识库）。
3. 与系统无关的通用问题照常回答。
我不能代替你执行操作：提交预约、审批、核销、报修等请在对应页面操作。提问有每分钟限流，历史会话保留 24 小时。', 30, 1),
(30019, '预约流程', '预约冲突检测与完整校验规则',
 '冲突,占用,时段冲突,约不了,约不上,禁止预约,不能预约,校验,被占用,为什么失败',
 '提交预约时的完整校验链（按顺序）：
1. 实验室须开放且允许预约（两个开关任一关闭都会被拒）。
2. 日期不能早于今天，结束时间须晚于开始时间。
3. 预约时段必须被该实验室当天某个启用的开放时段完整包含。
4. 使用人数不能超过实验室容量。
5. 设备故障锁定：实验室下任一设备故障即整间锁定；指定设备还须属于该实验室且无故障。
6. 时间冲突检测：与同实验室同日期的既有有效预约做区间交集判定，只要时段重叠即拒绝并提示冲突单号。
并发提交通过数据库行锁串行化，保证同一时段不会被两人同时约到。', 60, 1);

-- ============================================================================
-- 完成
SET FOREIGN_KEY_CHECKS = 1;

-- ---------- AI 知识库扩充（v2.9，30 条）----------
-- ============================================================================
-- v2.9 AI 助手知识库扩充
--   在 v2.2 的 19 条基础上新增 30 条（主键 30020-30049），覆盖：
--   预约时间限制、审批职责分离、签到与签退、报修状态机、设备台账口径、
--   实验室与开放时段、用户与权限、消息推送、统计口径、导出、安全、天气。
--
-- 幂等：INSERT IGNORE（主键固定），可重复执行；已存在的条目不会被覆盖。
--   修改条目：UPDATE ai_knowledge SET ... WHERE id = 30020;
--   执行后立即生效：~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

INSERT IGNORE INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES

-- 30020 预约时间限制 ----------------------------------------------------------
(30020, '预约流程', '预约开始后就不能再取消',
 '取消,不能取消,取消限制,已经开始,过了时间,还能取消吗,取消不了,撤销',
 '取消规则有明确的时间边界：
1. 待审核（PENDING）：任何时间都可以撤回，过期的申请也能撤回，避免僵尸单卡住流程。
2. 已通过（APPROVED）：只能在「预约开始时间之前」取消；一旦到达开始时间，系统会直接拒绝取消并提示「预约已开始或已结束，无法取消」。
3. 已完成 / 已驳回 / 已取消：都是终态，不能再次取消。
4. 取消成功后会自动作废对应的签到凭证，并给本人推送「预约已取消」消息；撤回待审核单还会通知审批人「无需再审批」。', 75, 1),

-- 30021 过期自动完成 ----------------------------------------------------------
(30021, '预约流程', '过期的已通过预约会自动变成已完成',
 '自动完成,过期,超时,没去,爽约,忘记签到,状态不变,定时任务',
 '系统每 5 分钟执行一次状态推进任务：
1. 预约单：结束时刻已过、状态仍为「已通过」的，自动流转为「已完成」。这样历史状态不会长期停留在已通过，也杜绝了过期单被取消的可能。
2. 签到单：超时未签到的从「待签到」变为「已过期」，并给预约人推送「预约已过期」提醒；已签到但未签退的，在结束时刻自动补签退并置为「已完成」，同时把预约单推进为已完成。
因此预约结束后你会看到状态自动变化，不需要手动操作。', 70, 1),

-- 30022 审批职责分离 ----------------------------------------------------------
(30022, '审批规则', '任何人不能审批自己提交的预约',
 '自己审批,自审,审批自己的,职责分离,自己的预约,能审批自己吗',
 '审批环节强制职责分离：提交人不能审批自己提交的预约。
即使用管理员账号尝试审批本人提交的单据，系统也会返回「不能审批自己提交的预约」。
这样审批控制才真正有效，避免「自己提交、自己通过」绕过流程。若确实需要调整自己的预约，应走取消后重新提交，或由其他审批人处理。', 70, 1),

-- 30023 教师预约审批人 --------------------------------------------------------
(30023, '审批规则', '教师提交的预约只能由管理员审批',
 '教师预约,老师预约,谁审批,教师自己审批,管理员审批,教师单',
 '审批权限按提交人身份区分：
1. 学生提交的预约：教师、实验室管理员、超级管理员都可以审批。
2. 教师提交的预约：只有实验室管理员与超级管理员可以审批，其他教师没有审批权。
3. 无论谁审批，都不能审批自己提交的单据。
权限不足时接口返回业务码 40300（无权限访问该资源），不会静默失败。', 68, 1),

-- 30024 审批通过后生成核销码 --------------------------------------------------
(30024, '签到核销', '审批通过后自动生成 6 位核销码',
 '核销码,签到码,审批通过,生成,六位,6位,哪里来的',
 '预约审批通过时会自动创建签到凭证，并生成一个全局唯一的 6 位数字核销码。
核销码的可见范围：
1. 预约本人：在「我的签到」里可以查看自己的核销码，到场出示给教师或管理员。
2. 超级管理员：可查看全部核销码，用于兜底处理异常签到。
3. 教师与实验室管理员：在核销页面只能输入核销码完成核销，列表与详情中核销码字段是脱敏的，避免提前获取他人核销码。', 70, 1),

-- 30025 签到时间窗 ------------------------------------------------------------
(30025, '签到核销', '什么时候可以签到核销',
 '什么时候签到,签到时间,提前签到,迟到,核销时间,时间窗',
 '签到核销有时间窗限制：只能在该预约的时段内完成核销，未到开始时间或已过结束时间都无法核销。
操作方式：预约人到场后出示 6 位核销码，教师或管理员在「签到核销」页输入核销码提交即可，核销成功后签到单状态变为「已签到」。
如果时段内没有完成核销，系统在结束时刻之后会把签到单置为「已过期」，并通知预约人。', 65, 1),

-- 30026 签到过期与爽约 --------------------------------------------------------
(30026, '签到核销', '没有签到的预约会怎样',
 '没签到,未签到,爽约,过期,签到过期,忘记签到,会怎样',
 '预约审批通过但本人在时段内没有签到核销时：
1. 签到单在预约结束时刻之后自动从「待签到」变为「已过期」。
2. 系统会推送一条「预约已过期」站内消息，说明因未在时段内签到而失效。
3. 如果这条预约之前已经签到但忘了签退，结束时刻会自动补签退，签到单置为「已完成」，预约单同步变为「已完成」。
签到状态与预约状态是联动的，不需要手动维护。', 65, 1),

-- 30027 签退规则 --------------------------------------------------------------
(30027, '签到核销', '签退怎么操作、什么时候算完成',
 '签退,离场,结束使用,已完成,怎么签退,离场签退',
 '签退用于记录实际离场时间：
1. 已签到的预约可以在「我的签到」里点签退，填写备注后提交，签到单状态变为「已完成」。
2. 如果忘记签退，预约结束时刻系统会自动补一个签退时间（取预约结束时刻），状态同样置为「已完成」。
3. 签到单完成后，关联预约单会同步流转为「已完成」，之后不能再取消。', 60, 1),

-- 30028 报修状态机 ------------------------------------------------------------
(30028, '设备报修', '报修单的四个状态与流转',
 '报修状态,待处理,维修中,已完成,已取消,报修流程,流转',
 '报修单共 4 个状态：
1. 待处理（PENDING）：提交后进入，设备与所属实验室被锁定，期间该实验室不能预约。
2. 维修中（REPAIRING）：处理人填写维修计划后进入，设备继续保持锁定。
3. 已完成（COMPLETED）：填写维修结果与验收说明后进入，设备与实验室自动解锁，可恢复预约。
4. 已取消（CANCELLED）：报修人本人可取消（需填写原因），取消后同样解锁。
已完成与已取消都是终态，不能再流转。', 65, 1),

-- 30029 重复报修限制 ----------------------------------------------------------
(30029, '设备报修', '同一台设备不能重复报修',
 '重复报修,已经报修,不能再报修,故障中,设备报修过了,重复提交',
 '为避免重复工单，同一台设备存在未结束的报修（待处理或维修中）时，再次提交报修会被拒绝，提示设备正在维修中。
已报废的设备（状态为报废）不会出现在报修可选设备清单里。
需要重新报修时，应先等原工单完成或被取消。', 60, 1),

-- 30030 报修锁定范围 ----------------------------------------------------------
(30030, '设备报修', '报修会锁定整间实验室，而不是单台设备',
 '锁定,实验室被锁,报修影响,不能预约,锁定范围,解锁',
 '设备故障的锁定粒度是「实验室」而非设备：
1. 实验室下只要有设备处于故障状态，或存在未结束的报修单，整间实验室都会被锁定，禁止新的预约。
2. 提交报修后立即锁定；维修完成或报修取消后自动解锁。
3. 审批通过前也会复核设备故障状态，防止审批期间设备突然故障仍被放行。
这样设计是为了避免带着故障设备做实验的安全风险。', 65, 1),

-- 30031 设备台账数量口径 ------------------------------------------------------
(30031, '设备台账', '设备台账按「同型号同配置多台」登记，总数按数量汇总',
 '设备总数,多少台,数量,统计口径,台账,quantity,汇总,一台一条吗',
 '设备台账采用「实验室设备配置」模型：
1. 一条记录代表同型号、同配置的一批设备，用「数量」字段记录这一批有多少台。
2. 驾驶舱里的设备总数按数量字段汇总，而不是按记录行数统计，所以 1 条数量为 10 的记录表示 10 台设备。
3. 维护台账时改数量即可调整规模，不需要为每台设备单独建一条记录。
4. 故障状态同样作用于整批设备，报修会锁定其所属实验室。', 62, 1),

-- 30032 实验室删除保护 --------------------------------------------------------
(30032, '实验室管理', '有预约记录的实验室不能删除',
 '删除实验室,不能删除,有预约,删不掉,数据保护',
 '为保护历史数据完整性，实验室只要存在关联预约记录就不能被删除，接口会直接拒绝并提示。
需要下线实验室时，建议改用「禁止预约」或「关闭开放」开关：
1. 禁止预约：保留台账与历史记录，但不再接受新的预约申请。
2. 关闭开放：该实验室在开放时段外不参与预约。
这样既能停用，也不会破坏已有的预约与统计记录。', 55, 1),

-- 30033 实验室开放开关 --------------------------------------------------------
(30033, '实验室管理', '实验室的「开放」与「可预约」两个开关有什么区别',
 '开放状态,禁止预约,可预约,开关,维护中,不开放',
 '实验室有两个独立开关：
1. 开放状态（openStatus）：控制实验室是否处于开放状态，关闭后视为未开放。
2. 是否可预约（enableReserve）：控制是否允许提交预约，关闭后该实验室显示为禁止预约。
提交预约时会依次校验这两个开关，任一不满足都会拒绝，并给出「未开放」或「已被禁止预约」的提示。
临时维护时把「可预约」关掉即可，不必删除实验室。', 55, 1),

-- 30034 开放时段按星期 --------------------------------------------------------
(30034, '开放时段', '开放时段按星期配置，保存是整表覆盖',
 '时段,星期,周几,周一,周末,批量保存,配置时段,覆盖',
 '开放时段按「实验室 + 星期」维度配置：
1. 每间实验室可以分别为周一至周日设置多个可预约时段，每个时段带名称、开始时间、结束时间与人数上限。
2. 保存采用「先清空再写入」的整表覆盖方式，页面上提交的是什么，库里就是什么。
3. 只有被某个启用时段完整包含的预约时段才允许提交，跨时段的预约会被拒绝。
4. 页面上时间统一显示为 HH:mm，不显示秒。
某天没有配置时段时，该实验室当天就不能预约，页面上会提示暂无开放时段。', 60, 1),

-- 30035 账号三态 --------------------------------------------------------------
(30035, '用户管理', '账号的三种状态：启用、禁用、待审核',
 '账号状态,待审核,禁用,启用,登录不了,注册后不能登录,状态',
 '账号状态分三种：
1. 启用（1）：可以正常登录与使用。
2. 禁用（0）：管理员手动禁用，登录会被拒绝；重新启用后即时生效。
3. 待审核（2）：自助注册后的默认状态，必须由管理员审核通过并分配至少一个角色后才能登录。
审核通过时会清空该账号的登录缓存，新分配的权限立即生效，不需要重新登录。', 60, 1),

-- 30036 默认密码与重置 --------------------------------------------------------
(30036, '用户管理', '新增用户的默认密码与密码重置',
 '默认密码,初始密码,123456,重置密码,忘记密码,改密码,密码规则',
 '密码相关规则：
1. 管理员新增用户时如果不填密码，系统使用默认初始密码 123456，并做 BCrypt 加密存储。
2. 管理员可以为指定用户重置密码，未填写新密码时同样重置为 123456。
3. 重置密码后该账号已签发的令牌会被吊销，本人会收到「登录密码已被重置」的站内消息。
4. 用户本人可以在「个人中心」修改密码，需要先校验原密码。
建议首次登录后立即修改默认密码。', 60, 1),

-- 30037 删除用户限制 ----------------------------------------------------------
(30037, '用户管理', '哪些用户不能被删除',
 '删除用户,不能删除,删除自己,有预约,有报修,删不掉',
 '以下情况不允许删除用户：
1. 不能删除当前登录的自己，防止误操作导致无人可管理。
2. 存在未结束预约（待审核或已通过）的用户不能删除，避免预约变成孤儿数据。
3. 存在进行中报修（待处理或维修中）的用户不能删除。
删除成功后还会清理该账号在 Redis 中的登录缓存，避免旧令牌继续可用。
如果只是想临时禁止登录，用「禁用」比删除更安全，历史记录也会保留。', 58, 1),

-- 30038 看不到菜单 / 40300 ----------------------------------------------------
(30038, '权限模型', '菜单看不到或提示 40300 是什么原因',
 '看不到,没权限,40300,菜单没了,拒绝访问,无权限,按钮没有',
 '看到的内容由当前账号的权限码决定，出现以下现象通常是权限不足：
1. 左侧菜单不显示：菜单要求至少命中一个权限码，账号没有对应权限就不渲染入口。
2. 页面能进但按钮没有：按钮各自校验自己的权限码，没有对应权限就不显示。
3. 接口返回业务码 40300「无权限访问该资源」：说明页面或按钮绕过了前端校验直接调用了无权接口。
处理方式：由管理员在「用户管理 → 分配角色」或「角色管理 → 权限分配」里补齐权限。
权限变更后系统会清空登录缓存，重新进入页面即可生效。', 65, 1),

-- 30039 权限粒度 --------------------------------------------------------------
(30039, '权限模型', '权限是按钮级的 RBAC 模型',
 '权限,粒度,按钮级,rbac,角色,52项权限,权限码,分配权限',
 '系统采用 RBAC（基于角色的访问控制）：
1. 权限以「权限码」形式定义，粒度到按钮，例如预约提交、预约审批、报修处理、用户新增。
2. 角色是权限的集合，用户通过绑定角色获得权限，一个用户可以绑定多个角色。
3. 接口层用方法级注解逐个校验权限码，前端菜单与按钮同时按同一套权限码控制显隐，保证「看不到就点不到」。
4. 当前系统共 4 个内置角色、52 项权限。权限或角色变更后会清空登录缓存，立即生效。', 62, 1),

-- 30040 消息推送方式 ----------------------------------------------------------
(30040, '消息通知', '新消息是怎么提醒我的',
 '消息推送,提醒,红点,未读数,系统通知,浏览器通知,怎么知道有新消息',
 '站内消息有三种提醒方式：
1. 顶栏红点：显示未读条数（超过 99 显示 99+），每 60 秒轮询一次，切换页面时也会刷新。
2. 浏览器系统通知：在浏览器允许通知权限后，新消息会弹出系统级通知，点击可直接跳转消息中心；首次点击页面时会请求一次授权。
3. 页内弹窗兜底：未授权或浏览器不支持系统通知时，改用页面内通知弹窗提示。
消息中心支持按类型筛选、标记已读、一键全部已读与删除。', 62, 1),

-- 30041 发送消息 --------------------------------------------------------------
(30041, '消息通知', '给指定人发消息与广播的区别',
 '发消息,群发,广播,全部人,通知学生,发送消息,怎么发',
 '消息发送支持三种范围：
1. 单个用户：指定收件人发送。
2. 批量：勾选多个用户，使用批量发送。
3. 全体广播：不指定收件人时向全部启用账号广播。
发送需要「消息发送」权限（管理员与教师具备）。消息类型分为系统通知、预约通知、报修通知，接收方在消息中心按类型筛选。
系统自身的业务消息（预约审批结果、报修进度、账号审核等）由后端自动推送，不需要手动发送。', 58, 1),

-- 30042 驾驶舱口径 ------------------------------------------------------------
(30042, '统计看板', '驾驶舱各项指标是怎么统计的',
 '统计口径,指标,怎么算,总数,今日预约,待审核,待处理,故障设备,概览',
 '驾驶舱概览的 8 项指标口径：
1. 实验室总数：实验室台账记录数。
2. 设备总数：按设备台账的「数量」字段汇总（一条记录可能代表多台）。
3. 用户总数：未删除的账号数。
4. 预约总次数：全部预约单数量。
5. 今日预约：预约日期为今天的单据数。
6. 待审核：状态为待审核的预约数。
7. 待处理报修：状态为待处理或维修中的报修数。
8. 故障设备：故障状态为故障的设备汇总数。
概览结果在 Redis 缓存，业务写操作后会自动失效并重算。', 70, 1),

-- 30043 看板口径 --------------------------------------------------------------
(30043, '统计看板', '实时占用看板的统计口径',
 '实时看板,占用,空闲,使用中,已预约,今日,未来预约,7天',
 '实时占用看板的判定规则：
1. 只有当天的预约才计入「使用中 / 已预约」统计，未来日期的预约不影响当天的占用状态。
2. 只有尚未结束的已通过预约才会被标记为占用或已预约。
3. 查询窗口是「今天起 7 天」，未来 7 天内的预约会作为「后续预约提示」展示在卡片上，并用醒目提示条标注，避免误以为该实验室完全没有预约。
4. 卡片只展示预约日期、时段与用途，不展示使用人信息，兼顾隐私与看板可读性。
5. 存在故障设备的实验室显示为「故障锁定」，不参与空闲统计。', 68, 1),

-- 30044 热门实验室 ------------------------------------------------------------
(30044, '统计看板', '热门实验室排行是怎么算的',
 '热门,排行,排名,top,热度,最受欢迎,使用最多',
 '热门实验室基于预约热度统计：
1. 每次预约提交成功，系统会在 Redis 的有序集合里给对应实验室热度加一。
2. 排行榜按热度倒序取前 N 名（默认前 10），展示实验室名称与预约次数。
3. 热度数据独立维护，不会因为查询历史预约而重复累加。
因此「热门实验室」反映的是实际被预约的次数，而不是实验室的容量或设备数量。', 60, 1),

-- 30045 Excel 导出 ------------------------------------------------------------
(30045, '数据导出', '预约记录导出 Excel 的范围与限制',
 '导出,excel,表格,下载,导出数据,上限,导不出来',
 '预约管理支持导出 Excel：
1. 导出会沿用当前的筛选条件（实验室、状态、关键词），不是无条件导出全表。
2. 单次导出行数上限为 2 万条；超过时会提示先用筛选条件缩小范围，避免一次性把整表读入内存导致服务压力过大。
3. 导出的列包括：预约单号、实验室、申请人、审批人、预约日期、开始/结束时间、人数、用途、状态、创建时间。
4. 导出采用流式写入，数据量较大时也不会占用过多内存。', 60, 1),

-- 30046 登录安全 --------------------------------------------------------------
(30046, '系统安全', '登录失败次数多了会怎样',
 '登录失败,锁定,密码错误,几次,暴力破解,无法登录,尝试次数',
 '登录接口带暴力破解防护：
1. 同一账号在 5 分钟内连续失败 5 次后会被临时锁定，之后即使密码正确也会提示「登录失败次数过多，请稍后再试」。
2. 锁定窗口为 5 分钟，时间到后自动解锁。
3. 登录成功会清零失败计数。
4. 无论账号是否存在、密码是否错误，返回的提示都是「账号或密码错误」，避免被用来探测账号是否存在。', 58, 1),

-- 30047 操作日志 --------------------------------------------------------------
(30047, '系统安全', '操作日志记录什么、保存多久',
 '操作日志,审计,日志,记录哪些,保留多久,谁操作的,脱敏',
 '操作日志由 AOP 自动记录所有关键写操作：
1. 记录内容包括：操作人、IP、请求方法、请求地址、请求参数、返回结果、耗时、成功或失败、异常信息。
2. 请求参数中的密码、令牌等敏感字段在落库前会被替换为 ******，不会保存明文凭据。
3. 日志异步落库，不阻塞主业务流程。
4. 默认保留 90 天，每天凌晨自动清理更早的记录；可通过环境变量 OPER_LOG_RETAIN_DAYS 调整保留天数（下限 7 天）。
只有具备操作日志权限的管理员可以查询。', 62, 1),

-- 30048 验证码 ------------------------------------------------------------------
(30048, '系统安全', '验证码收不到 / 验证码登录怎么用',
 '验证码,收不到,短信,验证码登录,登录验证码,取码',
 '验证码用于「验证码登录」「注册」「找回密码」三个场景：
1. 验证码有效期 5 分钟，同一标识有发送冷却限制，过于频繁会提示稍后再试。
2. 系统未接入真实短信通道时，演示环境会把验证码随响应返回给前端并弹窗提示。
3. 生产环境应关闭回传（环境变量 CAPTCHA_EXPOSE_CODE=false），改由短信通道下发，否则任何人知道账号就能匿名取码登录。
4. 验证码登录成功后，后续操作与密码登录完全一致。', 55, 1),

-- 30049 顶栏天气 --------------------------------------------------------------
(30049, '系统概览', '顶栏显示的天气是怎么来的',
 '天气,气温,温度,城市,定位,广州,和风,天气预报,顶栏',
 '顶栏天气由后端代理和风天气接口获取：
1. 显示内容包括天气图标、温度与城市名，悬停可看体感温度、湿度与风向风力。
2. 前端会尝试获取浏览器定位；拒绝授权或无法定位时默认显示广州。
3. 在非 HTTPS 地址下浏览器会禁止精确定位，因此会回退为默认位置。
4. 天气数据按经纬度缓存 10 分钟，页面每 30 分钟刷新一次。
5. 和风天气的 API Key 只保存在服务器端，前端无法获取；天气接口异常时只是隐藏天气，不影响其他功能。', 55, 1),

-- 30050 AI 代办操作 ------------------------------------------------------------
(30050, 'AI 助手', 'AI 助手能帮我做什么（代办预约/审批）',
 '代办,代我,帮我预约,帮我审批,自动提交,自动审批,替我预约,替我审批,帮我约,帮我批,能做什么,能帮我做什么',
 'AI 助手支持「代办操作」，用祈使句说明目的即可，无需一步步点页面：
1. 学生端：「帮我预约本周五第一计算机实验室」「我想约明天下午 2 点的人工智能创新实验室」——助手会解析实验室、日期与时段，自动挑一个可预约时段提交申请，并返回预约单号与待审核状态。
2. 教师/管理员端：「帮我审批所有待审核的预约」「帮我驳回待审核的预约」——助手会批量处理（单次最多 20 条），逐条给出通过/驳回结果。
3. 权限与业务规则完全沿用系统原有校验：没有提交或审批权限会被拒绝；不能审批自己提交的预约；时段冲突、实验室关闭、设备故障锁定等仍会拦截并说明原因。
4. 问句不会误触发操作：「预约规则是什么」「帮我看看我的预约状态」这类咨询仍走知识问答；只有明确的祈使句才会执行。
5. 代办结果会写入会话历史，可在 AI 对话中回看。', 80, 1);

-- ============================================================================
-- v3.2 AI 知识库优化：按代码真实行为校正业务规则，并补齐高频业务细节
--
-- 背景：v2.9 的知识条目中有几处与实现不一致（签到时间窗、锁定判定条件、
--       待处理报修口径、密码重置吊销范围、导出内存说明等），会让 AI 给出
--       错误结论；同时缺少「冲突判定状态集合」「人数上限口径」「报修处理权限」
--       等用户最常追问的细节。
--
-- 内容：
--   1. 修正 19 条既有条目（UPDATE，主键 30001-30045），并精修 30025/30030/30050 的关键词；
--   2. 新增 12 条业务细节（主键 30051-30062），写入采用 ON DUPLICATE KEY UPDATE，
--      重复执行会同步更新标题、关键词、正文与优先级。
--
-- 幂等：可重复执行。
-- 执行后立即生效：~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

UPDATE ai_knowledge SET content = CONCAT(content, '
14. AI 助手：业务规则问答与代办操作（预约提交、取消、审批、报修）。') WHERE id = 30001;

-- 30003 的关键词含泛化词「预约」，适当降低优先级，让更具体的条目在平票时胜出
UPDATE ai_knowledge SET priority = 58 WHERE id = 30003;

UPDATE ai_knowledge
SET content = REPLACE(content, '填写用途说明与使用人数（不能超过实验室容量）',
                            '填写用途说明与使用人数（不能超过实验室容量与时段人数上限）')
WHERE id = 30003;

UPDATE ai_knowledge
SET content = '预约单实际使用 5 种状态：待审核、已通过、已完成、已驳回、已取消。状态枚举里虽然定义了草稿，但系统不会创建草稿单，提交即为待审核。
流转规则：
1. 待审核：审批人可通过（变为已通过）或驳回（变为已驳回，可附驳回原因）。
2. 已通过：到场签到并签退后变为已完成；到达结束时刻仍未签退时，定时任务自动补签退并完成；开始前可以取消。
3. 只有待审核、已通过、已完成占用时段，已驳回、已取消不阻塞他人预约。
4. 已驳回、已完成、已取消为终态，不能再流转。'
WHERE id = 30004;

UPDATE ai_knowledge
SET content = '审批入口：「预约管理」页（需具备预约审批权限）。
核心规则：
1. 学生提交的预约：教师、实验室管理员、超级管理员均可审批。
2. 教师提交的预约：只有实验室管理员或超级管理员能审批。
3. 任何人不能审批自己提交的预约。
4. 仅「待审核」状态可审批；驳回原因不是必填，但建议填写，提交人会收到带原因的站内消息。
5. 审批通过时只复核设备故障状态，不重复校验时段冲突与实验室开关，因为时段冲突在提交时已做锁定校验。
6. 审批通过会自动生成 6 位签到核销码并通知预约人，同时写入一条审批流水（审批人、动作、意见、时间）。
7. 列表里的审批按钮按同一套规则计算是否可用，不可审批时会置灰。'
WHERE id = 30005;

UPDATE ai_knowledge
SET content = '取消入口：「我的预约」列表。
规则：
1. 只能取消本人提交的预约。
2. 仅「待审核」和「已通过」状态允许取消；已驳回、已完成、已取消为终态。
3. 时间边界：待审核的预约任何时间都能撤回；已通过的预约只能在开始时间之前取消，一到开始时间系统就拒绝并提示「预约已开始或已结束，无法取消」。
4. 取消原因可以填写但不是必填；取消后对应的签到核销凭证同步作废。
5. 撤回待审核的预约时，审批人会收到「无需再审批」的提醒，避免无效待办。
6. 取消后时段立即释放，其他人可以预约该时段。'
WHERE id = 30006;

UPDATE ai_knowledge
SET content = '签到闭环：预约审批通过 → 系统自动生成 6 位数字核销码 → 到场出示 → 核销签到 → 使用结束签退 → 预约完成。
要点：
1. 核销码在「我的签到」页查看，仅预约本人与超级管理员可见；教师等在核销页只能输入核销码，看不到码本身。
2. 签到时间窗：开始前 30 分钟至结束时刻，提前超过 30 分钟或结束之后都无法核销。
3. 核销人只需具备签到核销权限，不要求是预约本人，系统也不校验核销人与预约人的关系。
4. 重复核销会被状态机拦截，同一核销码只能成功一次。
5. 签退后预约单自动置为「已完成」；超时未签到由定时任务置为「已过期」并通知本人。'
WHERE id = 30007;

UPDATE ai_knowledge
SET content = '每间实验室按星期（周一至周日）配置若干开放时段，每个时段包含名称、开始与结束时间、人数上限。
配置与校验：
1. 保存采用整表覆盖：先删除该实验室的全部时段，再按页面提交的内容写入，页面是什么库里就是什么。
2. 预约时段必须被该实验室当天某个启用中的时段完整包含，跨时段或超出边界会被拒绝。
3. 时段上配置的人数上限（0 表示不限）会参与校验：单次预约人数不能超过所命中时段的人数上限，命中多个时段时取最严格的一条。
4. 默认作息：周一至周六每天 4 节（08:00-09:40、10:00-11:40、14:00-15:40、16:00-17:40），周日 2 节（上午两节），以实际配置为准。
5. 某天没有配置时段时，该实验室当天不能预约。'
WHERE id = 30009;

UPDATE ai_knowledge
SET content = '「实时占用看板」展示每间实验室的当前状态，判定按固定优先级从高到低：
1. 未开放：开放状态关闭或禁止预约任一成立。
2. 故障锁定：实验室下存在故障状态的设备。
3. 使用中：当前时刻落在今天某条已通过预约的时段内。
4. 已预约：今天还有未结束的已通过预约，或今天有待审核预约占位。
5. 空闲：以上都不满足。
其他说明：占用只看当天，未来日期的预约只作为「后续预约」提示；顶部使用率按「使用中 ÷（总数 - 故障锁定 - 未开放）」计算；看板缓存约 20 秒刷新。'
WHERE id = 30010;

UPDATE ai_knowledge
SET content = '设备损坏时的处理流程（「报修闭环」页）：
1. 任何登录用户均可提交报修：选择故障设备、填写故障描述，生成报修单，状态「待处理」。报废设备不在可选清单里，停用设备仍可以选择。
2. 提交后本人收到确认消息，具备报修处理权限的管理员或实验室管理员收到待办提醒。
3. 处理人受理后进入「维修中」，报修人收到受理通知。
4. 维修完成填写处理结果后置为「已完成」，报修人收到完成通知。
5. 只有报修人本人可以取消报修，管理员也不能代取消；完成或取消后设备解锁、实验室恢复可预约。
6. 报修期间该设备会被置为故障状态，其所属实验室随之暂停预约。'
WHERE id = 30012;

UPDATE ai_knowledge
SET content = '我是系统内置 AI 助手，能做的事分三类：
1. 查询实时数据：实验室实时占用、统计概览、热门实验室、我的预约、我的签到、故障设备、我的报修、未读消息；管理员还能看到待审核预约与待处理报修。
2. 解答业务知识：预约与取消规则、审批与职责分离、签到核销、开放时段、报修闭环、角色权限等，来自系统知识库。
3. 代办操作：预约提交、取消预约、审批（通过或驳回）、设备报修。用祈使句说出目的即可，权限与业务校验仍由系统执行。
不能做的事：代签到核销、修改已提交的预约内容、代发站内消息、绕过审批或权限。
数据只取当前登录人有权限看到的部分，不会跨用户查询。'
WHERE id = 30018;

UPDATE ai_knowledge
SET content = '提交预约时的完整校验链（按顺序）：
1. 实验室须开放且允许预约，任一开关关闭都会被拒。
2. 日期不能早于今天，系统没有「最多提前多少天」的限制。
3. 结束时间须晚于开始时间。
4. 预约时段必须被该实验室当天某个启用的开放时段完整包含。
5. 使用人数不能超过实验室容量，也不能超过所命中时段配置的人数上限（0 表示不限）。
6. 设备故障锁定：实验室下任一设备处于故障状态即整间锁定；指定设备还须属于该实验室且无故障。
7. 时间冲突检测：同实验室同日期，与待审核、已通过、已完成的预约做区间相交判断，重叠即拒绝并提示冲突单号；已取消、已驳回、草稿不占用时段。
并发提交时通过锁定实验室行串行化，保证同一时段不会被两人同时约到；同一用户在不同实验室可以各留一条待审核单。'
WHERE id = 30019;

UPDATE ai_knowledge
SET content = '不能。已通过的预约只能在开始时间之前取消，到达开始时间后接口直接拒绝并提示「预约已开始或已结束，无法取消」。
补充：
1. 待审核的预约不受时间限制，随时可以撤回。
2. 已驳回、已完成、已取消都是终态，也不能取消。
3. 取消后签到凭证作废、时段释放，本人会收到「预约已取消」消息；撤回待审核单还会通知审批人无需再审批。'
WHERE id = 30020;

UPDATE ai_knowledge
SET content = '签到核销有明确的时间窗：从预约开始前 30 分钟起，到预约结束时刻为止都可以核销。
1. 早于开始前 30 分钟：提示「尚未到达签到时间，最早可提前 30 分钟签到」。
2. 晚于结束时刻：签到凭证已被定时任务置为已过期，无法再核销。
操作方式：预约人到场出示 6 位核销码，教师或管理员在「签到核销」页输入核销码提交，核销成功后签到单变为「已签到」。',
    keywords = '什么时候签到,签到时间,提前签到,迟到,核销时间,时间窗,签到,提前多久,提前到,多久到,最早几点,提前多少分钟,签到要提前'
WHERE id = 30025;

UPDATE ai_knowledge
SET content = '为避免重复工单，同一台设备存在未结束的报修（待处理或维修中）时，再次提交报修会被拒绝，提示设备正在维修中。提交时会锁定设备行，防止并发重复提交。
设备能否报修：
1. 已报废的设备（运行状态为报废）不会出现在报修可选清单里，也不能提交。
2. 已停用的设备仍可以报修，因为停用不等于损坏。
3. 需要重新报修时，应先等原工单完成或被取消。'
WHERE id = 30029;

UPDATE ai_knowledge
SET content = '设备故障的锁定粒度是「实验室」而不是单台设备：
1. 实验室下只要存在故障状态的设备，整间实验室就被锁定，禁止新的预约。
2. 提交报修会把设备置为故障状态，从而锁定所属实验室；维修完成或报修取消后自动解除，实验室恢复可预约。
3. 提交预约与审批通过时都会检查设备故障状态，防止带病设备被使用。
4. 判定只看设备的故障状态，不看是否存在未结束的报修单，两者在正常流程下是一致的。
这样设计是为了避免带着故障设备做实验的安全风险。',
    keywords = '锁定,被锁,被锁定,故障锁定,实验室锁定,实验室被锁,报修影响,锁定范围,解锁'
WHERE id = 30030;

UPDATE ai_knowledge
SET content = '密码相关规则：
1. 管理员新增用户时不填密码，默认初始密码 123456，使用 BCrypt 加密存储。
2. 管理员重置密码时未填写新密码同样重置为 123456，并吊销该账号已签发的令牌、发送「登录密码已被重置」站内消息。
3. 用户本人修改密码需先校验原密码，修改成功后会吊销令牌，需要用新密码重新登录。
4. 登录页「忘记密码」自助重置会吊销该账号已签发的全部令牌，旧登录态立即失效，需要用新密码重新登录。
5. 退出登录同样会吊销令牌，旧 Token 立即失效。
建议首次登录后立即修改默认密码。'
WHERE id = 30036;

UPDATE ai_knowledge
SET content = '驾驶舱概览的 8 项指标口径：
1. 实验室总数：未删除的实验室台账数。
2. 设备总数：按设备台账「数量」字段汇总，一条记录可能代表多台。
3. 用户总数：未删除的账号数。
4. 预约总次数：全部未删除的预约单，含已取消与已驳回。
5. 今日预约：预约日期为今天、且状态不是已取消或已驳回的单据数。
6. 待审核：状态为待审核的预约数。
7. 待处理报修（未完成报修）：状态为待处理或维修中的报修数。
8. 故障设备：故障状态为故障的设备记录数，按记录条数统计而不是按数量汇总。
概览结果带 Redis 缓存，预约、签到、报修、设备等写操作后自动失效重算。'
WHERE id = 30042;

UPDATE ai_knowledge
SET content = '实时占用看板的判定规则：
1. 只有当天的预约才计入「使用中」与「已预约」，未来日期的预约不影响当天的占用状态。
2. 状态按固定优先级判定：未开放、故障锁定、使用中、已预约、空闲。
3. 查询窗口是从今天起未来 7 天，即今天加之后 7 天共 8 个自然日，未来预约只作为「后续预约提示」展示。
4. 卡片只展示预约日期、时段与用途，不展示使用人信息，兼顾隐私与看板可读性。
5. 存在故障设备的实验室显示为「故障锁定」；使用率按「使用中 ÷（总数 - 故障锁定 - 未开放）」计算。
6. 看板缓存 20 秒，概览与图表类统计缓存 5 分钟。'
WHERE id = 30043;

UPDATE ai_knowledge
SET content = '热门实验室基于预约热度统计：
1. 每次预约提交成功，系统会在 Redis 有序集合 hot:lab:rank 里给对应实验室热度加一。
2. 排行榜按热度倒序取前 N 名（默认前 10），展示实验室名称与预约次数。
3. 热度在提交时就累加，之后取消或驳回不会回退，因此反映的是「被发起预约的次数」。
4. Redis 里没有数据时（例如首次部署或缓存被清空），会回退为按历史预约分组统计并回填 Redis。
因此热门排行不等于当前有效预约数，也不代表实验室容量或设备数量。'
WHERE id = 30044;

UPDATE ai_knowledge
SET content = '预约管理支持导出 Excel：
1. 导出沿用当前筛选条件（实验室、状态、关键词），不是无条件导出全表。
2. 单次导出行数上限为 2 万条；超过会先提示总数并要求缩小范围，避免一次性把过多数据读入内存。
3. 导出列共 11 列：预约单号、实验室、申请人、审批人、预约日期、开始时间、结束时间、人数、用途、状态、创建时间。
4. 文件使用流式工作簿写出，但查询会一次性读取命中数据，因此务必用筛选条件控制范围。'
WHERE id = 30045;

-- 30050 去掉「报修」「驳回」「取消预约」等泛化关键词，避免抢走具体业务问题的命中
UPDATE ai_knowledge
SET keywords = '代办,代我,帮我预约,帮我审批,自动提交,自动审批,替我预约,替我审批,帮我约,帮我批,帮我取消,帮我报修,顺延,能做什么,能帮我做什么,代我操作,自动处理'
WHERE id = 30050;

INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES

-- 30051 提前预约范围 ----------------------------------------------------------
(30051, '预约流程', '最多能提前多久预约',
 '提前几天,最多约几天,能约多久以后,未来几天,下周能约吗,一个月后,几号能约,预约范围,最多能提前,提前几天预约,提前多少天,能提前几天,最早能约到',
 '预约日期没有「最多提前多少天」的限制：今天及以后任意日期都可以提交，只要该实验室当天配置了开放时段且时段未被占用。
但要注意：
1. 不能预约过去的日期，提交时会提示「预约日期不能早于今天」。
2. 只能预约已经配置开放时段的星期，某天没配时段就约不了。
3. 日期越远，排期越不确定，建议按实验室实际开放安排提前预约。', 62, 1),

-- 30052 冲突判定状态集合 ------------------------------------------------------
(30052, '预约流程', '时段冲突到底是怎么判定的',
 '时段冲突,时间冲突,冲突检测,被占用,重复预约,同一时段,两个人约,并发,冲突单号',
 '时间冲突的判定口径：
1. 只有同实验室、同日期、时段区间相交才算冲突，提示里会给出冲突单号。
2. 占用时段的状态是待审核、已通过、已完成三种；已取消、已驳回、草稿不占用。
3. 冲突判断不区分预约人，同一用户在同一实验室同一时段也只能有一条有效预约。
4. 不同实验室之间互不影响，同一用户可以同时约不同实验室。
5. 并发提交时会锁定该实验室的记录行串行处理，先提交成功的占位，后提交的收到冲突提示。', 70, 1),

-- 30053 人数上限口径 ----------------------------------------------------------
(30053, '预约流程', '预约人数上限按实验室容量还是时段容量',
 '人数上限,最多几个人,容量,超员,人数超了,能带几个人,时段人数,人数限制,几个人,三个人,两个人,人数',
 '预约人数受两道上限约束，取更严格的一个：
1. 实验室容量：使用人数不能超过实验室登记的容量。
2. 时段人数上限：所命中开放时段配置的人数上限（0 表示不限），命中多个时段时取最严格的一条。
3. 人数必须填写且至少为 1，没有默认值。
4. 是否真的能容纳这么多人，还需要结合实验室实际设备数量判断。', 60, 1),

-- 30054 预约单号规则 ----------------------------------------------------------
(30054, '预约流程', '预约单号是怎么生成的',
 '预约单号,单号,RSV,编号规则,怎么生成,单号是什么,单号规则',
 '预约单号由系统自动生成，格式为 RSV + 年月日时分秒 + 4 位随机字符，例如 RSV202610291014439C68。
1. 单号在提交时生成，用于在预约管理、审批、消息与导出中定位同一张单。
2. 审批时可以直接用单号指定单据。
3. 单号不是顺序号，不能用来推断提交顺序或总量。', 50, 1),

-- 30055 驳回原因与审批流水 ----------------------------------------------------
(30055, '审批规则', '驳回原因是否必填、审批流水记什么',
 '驳回原因,驳回理由,必须填原因,审批意见,审批流水,审批记录,审批历史,写原因,要写原因,原因必填,必须写原因',
 '驳回时填写原因不是强制校验，但强烈建议填写：
1. 不填也能驳回，提交人收到的消息里原因部分会为空。
2. 填写原因后，提交人会在站内消息和预约详情里看到具体驳回理由。
3. 每次审批都会写一条审批流水，记录审批人、动作（通过或驳回）、意见与时间，可在预约详情查看。
4. 被驳回的预约不能恢复，需要调整后重新提交。', 60, 1),

-- 30056 核销失败排查 ----------------------------------------------------------
(30056, '签到核销', '核销码输错、已被核销或核销失败怎么办',
 '核销码错误,输错,核销失败,已被核销,重复核销,无效核销码,核销不了,核销报错,输入错误,无效,核销不了怎么办,核销码',
 '核销失败的常见原因：
1. 核销码不存在或输错：提示核销码无效，请让预约人重新出示 6 位数字核销码。
2. 已经被核销过：同一核销码只能成功一次，重复提交会提示状态不允许。
3. 还没到签到时间：预约开始前 30 分钟才允许核销，提示尚未到达签到时间。
4. 已过结束时刻：签到凭证已过期，无法补签，需要重新预约。
5. 预约已取消或已驳回：对应签到凭证已作废。', 72, 1),

-- 30057 设备两种状态 ----------------------------------------------------------
(30057, '设备报修', '设备的运行状态和故障状态有什么区别',
 '运行状态,故障状态,停用,报废,在用,设备状态,设备故障,两个状态,停用设备,停用了,还能报修,报废设备',
 '设备有两个互相独立的状态字段：
1. 运行状态：在用、停用、报废，表示设备的资产使用状态，由管理员在设备台账维护。报废设备不能报修，也不会出现在报修可选清单里，停用设备仍可报修。
2. 故障状态：正常或故障，表示设备当前是否有故障，主要由报修流程自动维护，也可由管理员调整。
3. 只有故障状态会触发实验室锁定与预约拦截，运行状态本身不影响预约。
4. 处于故障状态的设备不能删除，需先完成或取消报修。', 60, 1),

-- 30058 报修处理与取消权限 ----------------------------------------------------
(30058, '设备报修', '谁能处理报修、谁能取消报修',
 '处理报修,维修权限,谁能修,取消报修,撤回报修,报修处理人,维修人员',
 '报修的处理权限：
1. 提交报修：任何登录用户都可以，学生、教师、管理员均可。
2. 受理与完成维修：需要报修处理权限，通常是实验室管理员或超级管理员。
3. 取消报修：只有报修人本人可以取消，管理员也不能代替取消。
4. 状态流转：待处理 → 维修中 → 已完成；待处理与维修中都可以取消，已完成与已取消是终态。
5. 完成或取消后设备解除故障状态，实验室恢复可预约。', 60, 1),

-- 30059 新建实验室默认未开放 --------------------------------------------------
(30059, '实验室管理', '新建的实验室为什么不显示、约不了',
 '新建实验室,新增实验室,实验室不显示,开放状态,默认关闭,约不了新实验室,开放开关,新建的实验室,新增的实验室,看不到,看不到实验室',
 '新增实验室后默认是「未开放」状态，这是有意设计：
1. 新增时系统会强制把开放状态置为关闭、可预约置为开启，需要管理员手动打开「开放」开关。
2. 未开放期间，该实验室在看板上显示「未开放」，也不会出现在预约可选的实验室下拉里。
3. 打开开放开关后还需要配置开放时段，否则当天仍提示未配置开放时段。
4. 临时维护只需关闭「可预约」开关，不必删除实验室。', 58, 1),

-- 30060 AI 数据来源与隐私 ------------------------------------------------------
(30060, 'AI 助手', 'AI 的数据从哪来、会不会看到别人的数据',
 '数据来源,隐私,会不会泄露,别人能看到,实时数据,AI看到什么,数据从哪来,数据安全,会不会看到,看到别人,别人,看到我的',
 'AI 助手的数据注入遵循最小必要原则：
1. 只注入当前登录人有权看到的数据，我的预约、我的签到、我的报修、我的消息都按本人过滤。
2. 待审核预约、待处理报修等全局待办只注入给超级管理员与实验室管理员。
3. 实验室占用、统计概览、热门排行属于全局公开数据，所有人可见。
4. 核销码、密码、令牌等敏感字段不会注入给模型。
5. 知识库按提问关键词计分取最相关的 5 条注入，不做全量投喂。', 60, 1),

-- 30061 AI 限流与会话 ----------------------------------------------------------
(30061, 'AI 助手', 'AI 助手的使用限制：限流、并发与会话保留',
 '限流,次数限制,太频繁,会话保留,历史记录,聊天记录保存,AI很慢,回答慢,并发',
 'AI 助手的使用限制：
1. 每个账号每分钟最多提问 20 次，超出会提示操作过于频繁。
2. 同一账号同一时间只能有一个回答在进行，重复发送会提示正在回答中。
3. 会话历史每个账号保留最近 20 条消息，24 小时后自动过期。
4. 回答采用流式输出，上游模型响应慢时首字会等待几秒。
5. 代办指令走本地解析，不经过模型，通常几百毫秒内返回结果。', 58, 1),

-- 30062 定时任务与自动流转 ----------------------------------------------------
(30062, '系统概览', '系统有哪些定时任务、会自动改哪些状态',
 '定时任务,自动完成,自动过期,自动签退,几点清理,自动解锁,状态自动变化,每5分钟',
 '系统内置定时任务，自动推进状态：
1. 每 5 分钟扫描一次：结束时刻已过但仍为已通过的预约自动置为已完成；超时未签到的签到凭证置为已过期并通知本人；已签到未签退的自动补签退并完成。
2. 每天凌晨 3 点 30 分清理超过保留期限的操作日志，默认保留 90 天。
3. 统计与看板采用缓存加版本号失效，不需要定时重算。
4. 报修完成或取消时立即解锁设备，不依赖定时任务。', 55, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    title = VALUES(title),
    keywords = VALUES(keywords),
    content = VALUES(content),
    priority = VALUES(priority),
    status = VALUES(status);
