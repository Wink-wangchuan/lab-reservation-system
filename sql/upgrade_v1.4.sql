-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.3.0 -> v1.4.0
--
--  本脚本用于【已部署过 v1.3.0 数据库】的在线升级，幂等可重复执行：
--   为管理端台账类实体补充乐观锁版本列 `version`，与实体上的 @Version 注解配套，
--   防止多个管理员并发维护同一条记录时互相覆盖（冲突时返回可读提示而非静默丢写）。
--
--    设备      device.version
--    实验室    lab_info.version
--    用户      sys_user.version
--    角色      sys_role.version
--    权限      sys_permission.version
--
--  执行顺序要求：先执行本脚本补齐列，再发布新版本应用，
--  否则新代码的 UPDATE 会因缺少 version 列而报错。
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    先导出连接参数环境变量，再用 SqlRunner 执行本脚本：
--    set -a && . ~/lab-reservation/.env && set +a
--    ~/opt/jre21/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> \
--        SqlRunner @upgrade_v1.4.sql
--
--  说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema
--        查询 + 动态 SQL 实现条件加列，保证脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- ----------------------------------------------------------------------------
-- 1. device.version（设备台账并发维护）
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device' AND COLUMN_NAME = 'version');

SET @s := IF(@c = 0,
    'ALTER TABLE device ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(管理端并发维护)'' AFTER remark',
    'SELECT ''device.version 已存在，跳过'' AS note');

PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ----------------------------------------------------------------------------
-- 2. lab_info.version（实验室台账）
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_info' AND COLUMN_NAME = 'version');

SET @s := IF(@c = 0,
    'ALTER TABLE lab_info ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(管理端并发维护)'' AFTER remark',
    'SELECT ''lab_info.version 已存在，跳过'' AS note');

PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ----------------------------------------------------------------------------
-- 3. sys_user.version（用户台账）
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'version');

SET @s := IF(@c = 0,
    'ALTER TABLE sys_user ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(管理端并发维护)'' AFTER status',
    'SELECT ''sys_user.version 已存在，跳过'' AS note');

PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ----------------------------------------------------------------------------
-- 4. sys_role.version（角色台账）
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'version');

SET @s := IF(@c = 0,
    'ALTER TABLE sys_role ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(管理端并发维护)'' AFTER status',
    'SELECT ''sys_role.version 已存在，跳过'' AS note');

PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ----------------------------------------------------------------------------
-- 5. sys_permission.version（权限台账）
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_permission' AND COLUMN_NAME = 'version');

SET @s := IF(@c = 0,
    'ALTER TABLE sys_permission ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(管理端并发维护)'' AFTER status',
    'SELECT ''sys_permission.version 已存在，跳过'' AS note');

PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

-- 校验
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'version'
ORDER BY TABLE_NAME;

-- 完成
