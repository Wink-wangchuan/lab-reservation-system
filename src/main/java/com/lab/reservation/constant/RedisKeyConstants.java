package com.lab.reservation.constant;

import cn.hutool.core.util.StrUtil;

/**
 * Redis 缓存 Key 常量集中管理
 *
 * <p>统一 key 命名规范：{模块}:{业务}:{维度}，防止缓存键魔法字符串散落，
 * 便于 Redis 可视化工具按前缀检索与批量失效。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /** 格式化品牌级缓存键：auth:user:123形式 */
    public static String userKey(Long userId) {
        return StrUtil.format(AUTH_USER, userId);
    }

    /** 格式化登录令牌键 */
    public static String tokenKey(Long userId) {
        return StrUtil.format(LOGIN_TOKEN, userId);
    }

    /** 格式化实验室详情键 */
    public static String labKey(Long labId) {
        return StrUtil.format(LAB_DETAIL, labId);
    }

    /** 格式化令牌吊销标记键：auth:revoked:<userId> */
    public static String revokedKey(Long userId) {
        return StrUtil.format(TOKEN_REVOKED, userId);
    }

    /** 格式化设备故障锁定键 */
    public static String labLockKey(Long labId) {
        return StrUtil.format(LAB_LOCK, labId);
    }

    /**
     * 组装统计结果缓存键：stat:cache:{业务}:v{版本}
     *
     * <p>缓存键内嵌版本号，写操作只需自增版本号即可让全部统计缓存整体失效，
     * 无需 KEYS 扫描，旧键由 TTL 自然过期。</p>
     */
    public static String statCacheKey(String biz, String version) {
        return StrUtil.format(STAT_CACHE, biz, version);
    }

    /** 登录 Token 前缀（jwt:<userId>）：用于单点登录/主动下线 */
    public static final String LOGIN_TOKEN = "login:token:{}";

    /** 登录用户缓存（LoginUser）：auth:user:<userId> */
    public static final String AUTH_USER = "auth:user:{}";

    /**
     * 令牌吊销标记：auth:revoked:<userId>
     *
     * <p>密码变更（本人改密 / 管理员重置）后写入该标记并保留到原令牌自然过期为止，
     * 使已签发的旧 JWT 立即失效（JWT 本身无状态、无法主动作废，故用标记兜底）；
     * 下一次成功登录时清除该标记，用户即可正常获得新令牌。</p>
     */
    public static final String TOKEN_REVOKED = "auth:revoked:{}";

    /** 用户权限缓存：perms:user:<userId> */
    public static final String USER_PERMS = "perms:user:{}";

    /** 热门实验室排行榜（ZSet）：hot:lab:rank */
    public static final String HOT_LAB_RANK = "hot:lab:rank";

    /** 实验室详情缓存：lab:detail:<labId> */
    public static final String LAB_DETAIL = "lab:detail:{}";

    /** 设备故障锁定 key：lab:lock:<labId>（值为 1 表示锁定） */
    public static final String LAB_LOCK = "lab:lock:{}";

    /** 统计缓存版本号：统计相关写操作自增该值，实现统计缓存整体失效 */
    public static final String STAT_CACHE_VERSION = "stat:cache:version";

    /** 统计结果缓存：stat:cache:{业务}:v{版本} */
    public static final String STAT_CACHE = "stat:cache:{}:v{}";

    /** 实验室实时占用看板缓存：board:realtime:v{版本}（短 TTL，兼顾实时性与刷新压力） */
    public static final String BOARD_REALTIME = "board:realtime:v{}";

    /** 组装实时看板缓存键 */
    public static String boardRealtimeKey(String version) {
        return StrUtil.format(BOARD_REALTIME, version);
    }

    /** 格式化验证码键：captcha:{场景}:{标识} */
    public static String captchaKey(String scene, String identity) {
        return StrUtil.format(CAPTCHA, scene, identity);
    }

    /** 格式化验证码发送节流键：captcha:cooldown:{场景}:{标识} */
    public static String captchaCooldownKey(String scene, String identity) {
        return StrUtil.format(CAPTCHA_COOLDOWN, scene, identity);
    }

    /** 验证码存储：captcha:{场景}:{标识}（值为 6 位数字，一次性消费） */
    public static final String CAPTCHA = "captcha:{}:{}";

    /** 验证码发送节流：captcha:cooldown:{场景}:{标识} */
    public static final String CAPTCHA_COOLDOWN = "captcha:cooldown:{}:{}";

    /** AI 会话历史（Redis List，按用户缓存最近若干条消息）：ai:chat:{userId} */
    public static final String AI_CHAT = "ai:chat:{}";

    /** AI 提问限流计数（每分钟）：ai:rate:{userId} */
    public static final String AI_RATE = "ai:rate:{}";

    /** AI 并发占用标记（同一用户同时只允许一条流）：ai:busy:{userId} */
    public static final String AI_BUSY = "ai:busy:{}";

    /** AI 知识库缓存（启用条目 JSON 列表，10 分钟过期）：ai:kb:list */
    public static final String AI_KB = "ai:kb:list";

    /** AI 代办待澄清上下文（用户下一步回答后继续执行）：ai:action:pending:{userId} */
    public static final String AI_ACTION_PENDING = "ai:action:pending:{}";

    /** 登录失败计数（防暴力破解）：login:fail:{username} */
    public static final String LOGIN_FAIL = "login:fail:{}";

    /** 天气实况缓存（按经纬度，10 分钟）：weather:now:{lon},{lat} */
    public static final String WEATHER_NOW = "weather:now:{}";

    /** 组装天气实况缓存键 */
    public static String weatherKey(String location) {
        return StrUtil.format(WEATHER_NOW, location);
    }

    /** 组装登录失败计数键 */
    public static String loginFailKey(String username) {
        return StrUtil.format(LOGIN_FAIL, username);
    }

    /** 组装 AI 会话历史键 */
    public static String aiChatKey(Long userId) {
        return StrUtil.format(AI_CHAT, userId);
    }

    /** 组装 AI 提问限流键 */
    public static String aiRateKey(Long userId) {
        return StrUtil.format(AI_RATE, userId);
    }

    /** 组装 AI 并发占用键 */
    public static String aiBusyKey(Long userId) {
        return StrUtil.format(AI_BUSY, userId);
    }

    /** 组装 AI 代办待澄清上下文键 */
    public static String aiActionPendingKey(Long userId) {
        return StrUtil.format(AI_ACTION_PENDING, userId);
    }
}