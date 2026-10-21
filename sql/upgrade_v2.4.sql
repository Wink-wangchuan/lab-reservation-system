-- ============================================================================
-- v2.4 教师可提交报修并查看报修工单
--
-- 背景：教师原本没有任何 device:repair:* 权限，左侧菜单不显示「报修闭环」。
-- 实际场景中教师在实验课上先发现设备损坏，应能直接提交报修并跟踪进度。
--
-- 授予：
--   1502 device:repair:submit  提交报修（同时放开 /repair/mine「我的报修」）
--   1504 device:repair:list    查看报修工单列表
-- 不授予：
--   1503 device:repair:handle  开始/完成维修 —— 维修仍由实验室管理员处理，保持职责分离
--
-- 幂等：唯一键 uk_role_perm(role_id, permission_id) + INSERT IGNORE。
-- 执行后必做：清登录/权限缓存，让已登录教师立即拿到新权限
--   ~/opt/redis/bin/redis-cli --scan --pattern 'auth:user:*' | xargs -r ~/opt/redis/bin/redis-cli del
--   ~/opt/redis/bin/redis-cli --scan --pattern 'perms:user:*' | xargs -r ~/opt/redis/bin/redis-cli del
-- ============================================================================

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(9201, 3, 1502),   -- device:repair:submit 提交报修
(9202, 3, 1504);   -- device:repair:list   报修查询

-- 同步 AI 知识库中的教师能力描述，否则问「教师能做什么」会漏掉报修
UPDATE ai_knowledge
SET content = REPLACE(content,
    '向学生/教师/实验室发送站内消息、提交实验室预约（教师提交的预约只有管理员能审批）。',
    '向学生/教师/实验室发送站内消息、提交实验室预约（教师提交的预约只有管理员能审批）、提交设备报修并查看报修工单（维修处理仍由实验室管理员负责）。')
WHERE id = 30002;

-- 校验：应返回 2 行
SELECT r.role_code, p.perm_code, p.perm_name
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'TEACHER'
  AND p.perm_code IN ('device:repair:submit', 'device:repair:list');
