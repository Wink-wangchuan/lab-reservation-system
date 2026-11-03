package com.lab.reservation.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内消息 SSE 连接注册表
 *
 * <p>每个登录用户可持有多个连接（多标签页/桌面端），消息推送时按 userId 找到全部连接；
 * 连接断开、超时或发送失败时立即摘除，避免连接泄漏。每 25 秒发送一次注释心跳，
 * 防止反向代理/浏览器把长时间无数据的连接判定为空闲而断开。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Component
public class NoticeStreamRegistry {

    private final Map<Long, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    /** 单连接最长存活 30 分钟，到期后前端会自动重连，避免无限长连接占用资源 */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        connections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        try {
            // 首帧让前端确认连接已建立
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    public void publish(Long userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }
        Set<SseEmitter> set = connections.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        set.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("notice").data(payload));
                return false;
            } catch (Exception e) {
                log.debug("[SSE] 推送失败，摘除连接：{}", e.getMessage());
                return true;
            }
        });
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        connections.forEach((userId, set) -> set.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                return false;
            } catch (Exception e) {
                return true;
            }
        }));
    }

    /** 当前在线连接数（可供健康检查/监控使用） */
    public int onlineUsers() {
        return (int) connections.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .count();
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = connections.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                connections.remove(userId);
            }
        }
    }
}
