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
    KEY idx_device_lab (lab_id)
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
-- 教师(role 3): 审批预约、查看统计、核销签到、消息中心（含发送消息）、可自行预约实验室
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(3001, 3, 1401),(3002, 3, 1405),(3003, 3, 1406),
(9103, 3, 1402),(9104, 3, 1403),(9105, 3, 1404),
(3004, 3, 1601),
(9011, 3, 1901),(9012, 3, 1902),(9013, 3, 1903),(9014, 3, 1904),
(9015, 3, 2001),(9016, 3, 2002),(9017, 3, 2003),(9106, 3, 2004),
(9018, 3, 2101),(9019, 3, 2102);
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
2. 教师：在学生全部能力之上，可审批学生提交的预约、在「签到核销」页输入核销码为学生核销签到（看不到核销码本身）、向学生/教师/实验室发送站内消息、提交实验室预约（教师提交的预约只有管理员能审批）。
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
3. 取消需填写原因；取消后对应的签到核销凭证同步作废。
4. 撤回待审核的预约时，审批人会收到「无需再审批」的提醒，避免无效待办。', 50, 1),
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
