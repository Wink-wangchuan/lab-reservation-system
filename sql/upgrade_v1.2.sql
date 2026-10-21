-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.1.0 -> v1.2.0
--
--  本脚本用于【已部署过 v1.1.0 数据库】的在线升级，幂等可重复执行：
--   1. sys_user.status 语义扩展：0禁用 / 1启用 / 2待审核（自助注册初始状态）
--   2. sys_user 增加 uk_phone 唯一索引（手机号已成为验证码登录与找回密码的身份标识）
--
--  【执行前必须先确认无重复手机号】：
--    SELECT phone, COUNT(*) c FROM sys_user
--    WHERE phone IS NOT NULL GROUP BY phone HAVING c > 1;
--    预期结果为空；若不为空，需先去重再执行本脚本，否则建索引会失败。
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    set -a; . ~/lab-reservation/.env; set +a
--    ~/opt/jre25/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> \
--        SqlRunner upgrade_v1.2.sql
--
--  说明：MySQL 8 不支持 CREATE INDEX IF NOT EXISTS，故用 information_schema
--        查询 + 动态 SQL 实现条件建索引，保证脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- ----------------------------------------------------------------------------
-- 1. 扩展账号状态语义（仅注释变更，不影响既有数据；TINYINT 可容纳取值 2）
-- ----------------------------------------------------------------------------
ALTER TABLE sys_user
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '状态 0禁用 1启用 2待审核(自助注册,需管理员审核)';

-- ----------------------------------------------------------------------------
-- 2. 手机号唯一索引（仅当尚未存在时才创建）
-- ----------------------------------------------------------------------------
SET @uk_exists := (SELECT COUNT(*)
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sys_user'
                     AND INDEX_NAME = 'uk_phone');

SET @uk_sql := IF(@uk_exists = 0,
    'ALTER TABLE sys_user ADD UNIQUE KEY uk_phone (phone)',
    'SELECT ''uk_phone 已存在，跳过'' AS note');

PREPARE uk_stmt FROM @uk_sql;
EXECUTE uk_stmt;
DEALLOCATE PREPARE uk_stmt;

-- 完成
