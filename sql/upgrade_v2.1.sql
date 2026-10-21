-- ============================================================================
-- v2.1 教师可发送站内消息
--   教师（role_id = 3）原只有 notice / notice:mine / notice:list（查看类），
--   现补授 notice:send(2004)，可在消息中心向学生/教师/实验室定向或批量发送消息。
--   发送目标下拉走 GET /api/user/options，该接口已对 notice:send 放行，无需额外授权。
--
-- 幂等：唯一键 uk_role_perm(role_id, permission_id) + INSERT IGNORE。
-- 执行后必做：清 Redis 登录缓存，让已登录教师立即拿到新权限
--   ~/opt/redis/bin/redis-cli -h 127.0.0.1 -p 6379 FLUSHALL
-- ============================================================================

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9106, 3, 2004);   -- notice:send 发送消息

-- 校验：应返回 1 行
SELECT r.role_code, p.perm_code
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'TEACHER' AND p.perm_code = 'notice:send';
