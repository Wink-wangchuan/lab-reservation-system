package com.lab.reservation.serviceImpl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lab.reservation.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 外部通知渠道（飞书 / 企业微信机器人 Webhook）
 *
 * <p>未配置地址或总开关关闭时静默跳过，不影响站内消息主流程；
 * 推送失败只记日志，不抛异常，避免外部服务抖动反向影响业务事务。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeChannelService {

    private final SysConfigService configService;

    private static final String KEY_ENABLED = "notify.external.enabled";
    private static final String KEY_FEISHU = "notify.webhook.feishu";
    private static final String KEY_WECOM = "notify.webhook.wecom";

    /** 外部 webhook 只接受 5 秒内响应，避免异步线程长时间挂起 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Async
    public void dispatch(String title, String content) {
        if (!configService.getBoolean(KEY_ENABLED, false)) {
            return;
        }
        String feishu = configService.get(KEY_FEISHU, "");
        String wecom = configService.get(KEY_WECOM, "");
        String text = title + "\n" + (content == null ? "" : content);

        if (isHttps(feishu)) {
            JSONObject body = new JSONObject()
                    .set("msg_type", "text")
                    .set("content", new JSONObject().set("text", text));
            post("飞书", feishu, body.toString());
        }
        if (isHttps(wecom)) {
            JSONObject body = new JSONObject()
                    .set("msgtype", "text")
                    .set("text", new JSONObject().set("content", text));
            post("企业微信", wecom, body.toString());
        }
    }

    private void post(String channel, String url, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("[通知] {} webhook 返回 {}：{}", channel,
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[通知] {} webhook 推送失败：{}", channel, e.getMessage());
        }
    }

    /** 只允许 HTTPS，避免配置成 http 明文地址把消息内容暴露在链路上 */
    private boolean isHttps(String url) {
        return url != null && url.startsWith("https://");
    }
}
