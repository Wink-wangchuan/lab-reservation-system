package com.lab.reservation.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置属性
 *
 * <p>绑定 {@code ai.*} 配置项。其中 <b>token 只从服务器环境变量注入</b>
 * （systemd 单元通过 {@code EnvironmentFile=%h/lab-reservation/.env} 提供），
 * 绝不写入仓库、也不下发到前端。</p>
 *
 * @author LabWise
 * @since 1.6.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** OpenAI 兼容协议的基础地址（不含 /chat/completions） */
    private String baseUrl;

    /** 访问令牌（仅来自环境变量 AI_TOKEN） */
    private String token;

    /** 模型名称 */
    private String model;

    /**
     * 上游空闲超时秒数（看门狗用，非 HTTP 请求级超时）
     *
     * <p>语义是「连续多少秒没有收到任何上游数据」才判定卡死并中断，
     * 而不是整个回答的总时长限制 —— 流式输出下回答本身可能持续数分钟，
     * 按总时长掐断会在中途切掉正常生成的内容。</p>
     */
    private Integer timeoutSeconds = 60;

    /** Redis 中保留的最近消息条数 */
    private Integer maxHistory = 20;

    /** 单用户每分钟最大提问次数 */
    private Integer rateLimitPerMinute = 20;

    /**
     * 是否已配置可用；token 为空即视为未配置，接口直接给出友好提示
     */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(token)
                && StrUtil.isNotBlank(baseUrl)
                && StrUtil.isNotBlank(model);
    }

    /**
     * 拼接 chat completions 的完整地址
     */
    public String chatCompletionsUrl() {
        return StrUtil.removeSuffix(baseUrl.trim(), "/") + "/chat/completions";
    }
}
