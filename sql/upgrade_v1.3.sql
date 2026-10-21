-- ============================================================================
--  智慧实验室预约管理系统 - 增量升级脚本 v1.2.0 -> v1.3.0
--
--  修复：学生（STUDENT）角色缺少 statistics:view 权限，导致「数据驾驶舱」
--        菜单被权限过滤掉、且登录后默认落地页 /dashboard 调 /stats/overview 返回 40300。
--
--  本脚本幂等可重复执行。
--
--  执行方式（服务器无 mysql 客户端，走 JDBC）：
--    set -a; . ~/lab-reservation/.env; set +a
--    ~/opt/jre25/bin/java -cp <SqlRunner.class 目录>:<mysql-connector.jar> SqlRunner upgrade_v1.3.sql
--
--  执行后必须清一次登录缓存，否则已登录学生的权限集合仍是旧的（菜单不会出现）：
--    ~/opt/redis/bin/redis-cli -h 127.0.0.1 -p 6379 FLUSHALL
--    （清缓存不会踢下线：JwtAuthenticationFilter 缓存未命中时会回源数据库重建）
-- ============================================================================

SET NAMES utf8mb4;
USE lab_reservation;

-- 为学生角色补授「数据驾驶舱」权限（1601 = statistics:view）
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES (9027, 4, 1601);

-- 完成
