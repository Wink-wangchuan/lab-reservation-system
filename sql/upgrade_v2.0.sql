-- ============================================================================
-- v2.0 教师可预约实验室
--   教师（role_id = 3）原只有「审批预约/预约列表」权限，无法自己发起预约。
--   现补授：提交预约(1402)、我的预约(1403)、取消预约(1404)。
--   教师仍不具备 reservation:export，导出按钮不会出现在其页面（前端按权限显隐）。
--
-- 幂等：唯一键 uk_role_perm(role_id, permission_id) + INSERT IGNORE，可重复执行。
-- 执行后必做：清 Redis 登录缓存，否则已登录教师仍使用旧权限集
--   ~/opt/redis/bin/redis-cli -h 127.0.0.1 -p 6379 FLUSHALL
-- ============================================================================

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9103, 3, 1402),   -- reservation:submit 提交预约
(9104, 3, 1403),   -- reservation:mine   我的预约
(9105, 3, 1404);   -- reservation:cancel 取消预约

-- 校验：应返回 3 行
SELECT r.role_code, p.perm_code
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'TEACHER'
  AND p.perm_code IN ('reservation:submit', 'reservation:mine', 'reservation:cancel')
ORDER BY p.perm_code;
