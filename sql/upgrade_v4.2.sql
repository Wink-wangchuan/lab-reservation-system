-- ============================================================================
-- v4.2 登录态策略（知识库 30111）
--
-- 背景：此前 JWT 固定 24 小时 + token 存 localStorage，导致「昨天登录、今天仍在线」。
-- 现改为：默认会话级（关闭浏览器退出）+ 可选「7 天免登录」+ 30 分钟无操作自动退出。
-- 幂等：ON DUPLICATE KEY UPDATE，可重复执行。
-- 执行后：~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES
(30111, '系统安全', '登录态保持多久，关闭浏览器会退出吗',
 '登录态,保持,多久,自动退出,关闭浏览器,免登录,7天,记住我,30分钟,无操作,还在登录',
 '1. 默认是「会话级」：不勾选「7 天免登录」时，令牌只存在当前浏览器会话中，关闭浏览器即退出。
2. 勾选「7 天免登录」后，令牌有效期 7 天，浏览器重启仍保持登录；普通令牌有效期为 8 小时。
3. 无论是否勾选，连续 30 分钟无操作都会自动退出登录。
4. 修改密码、管理员重置密码或主动退出登录，都会立即让此前签发的令牌失效。', 60, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    title = VALUES(title),
    keywords = VALUES(keywords),
    content = VALUES(content),
    priority = VALUES(priority),
    status = VALUES(status);

SELECT COUNT(*) AS entry FROM ai_knowledge WHERE id = 30111;
