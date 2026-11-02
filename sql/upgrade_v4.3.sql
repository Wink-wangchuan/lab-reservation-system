-- ============================================================================
-- v4.3 登录态与「记住我」调整（更新知识 30111）
--
-- 新策略：
--   - 登录态始终会话级（sessionStorage），关闭浏览器即退出；
--   - 「记住我」仅记住账号密码用于登录页回填，不保持登录状态；
--   - 连续 1 小时无操作自动退出登录。
--
-- 幂等：ON DUPLICATE KEY UPDATE，可重复执行。
-- 执行后：~/opt/redis/bin/redis-cli DEL ai:kb:list
-- ============================================================================

INSERT INTO ai_knowledge (id, category, title, keywords, content, priority, status) VALUES
(30111, '系统安全', '登录态保持多久，关闭浏览器会退出吗',
 '登录态,保持,多久,自动退出,关闭浏览器,记住我,账号密码,自动填充,1小时,60分钟,无操作,免登录',
 '1. 登录态始终是会话级：令牌只保存在当前浏览器会话中，关闭浏览器即退出。
2. 连续 1 小时无操作会自动退出登录；点击、输入、滚动等操作都会重新计时。
3. 登录页的「记住我」只记住账号和密码，用于下次打开登录页自动填充，不会让登录态跨浏览器保持。
4. 修改密码、重置密码或主动退出登录，都会立即让此前签发的令牌失效。', 60, 1)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    title = VALUES(title),
    keywords = VALUES(keywords),
    content = VALUES(content),
    priority = VALUES(priority),
    status = VALUES(status);

SELECT COUNT(*) AS entry FROM ai_knowledge WHERE id = 30111;
