package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 健康检查接口（匿名可访问）
 *
 * <p style="color:#c00"><b>用途：</b>给服务器侧的巡检脚本一个稳定的探针。</p>
 * 与 Spring Boot Actuator 不同，这里只暴露「数据库 / Redis 是否可用」这类必要条件，
 * 不返回配置、Bean、环境变量等可被用来侦察的信息。</p>
 *
 * <p>探测失败不会抛出异常：依赖不可用时也要正常返回结果，
 * 否则巡检脚本无法区分「服务挂了」和「服务活着但依赖挂了」。</p>
 *
 * @author LabWise
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping
    public Result<Map<String, Object>> health() {
        String db = probe(() -> jdbcTemplate.queryForObject("SELECT 1", Integer.class) != null);
        String redis = probe(() -> {
            stringRedisTemplate.opsForValue().get("health:probe");
            // get 在连接不可用时会抛异常，能走到这里即视为可用
            return true;
        });
        boolean up = "UP".equals(db) && "UP".equals(redis);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DEGRADED");
        body.put("db", db);
        body.put("redis", redis);
        body.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success(body);
    }

    /** 执行一次探测：异常时返回 DOWN:原因（截断，避免把堆栈带进响应） */
    private String probe(Supplier<Boolean> action) {
        try {
            return Boolean.TRUE.equals(action.get()) ? "UP" : "DOWN";
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[健康检查] 依赖不可用：{}", message);
            return "DOWN:" + message.substring(0, Math.min(60, message.length()));
        }
    }
}
