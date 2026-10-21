package com.lab.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 助手基础设施配置
 *
 * <p style="color:#c00"><b>答辩亮点：流式转发不占用 Tomcat 工作线程</b></p>
 * 流式对话是长连接，若直接在 Controller 里阻塞读写，会把 Tomcat 的请求线程占满。
 * 这里提供两个 Bean：</p>
 * <ul>
 *   <li>{@code aiExecutor}：专用的转发线程池（不用 Spring 默认的 {@code applicationTaskExecutor}，
 *       避免长流把默认池与无界队列打满）</li>
 *   <li>{@code aiHttpClient}：单例 {@link HttpClient}，复用连接、限定 connect 超时，
 *       并强制 HTTP/1.1（规避 h2 升级与流控带来的长流不确定性）</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.6.0
 */
@Configuration
public class AiConfig {

    /**
     * AI 流式转发线程池
     */
    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor aiExecutor() {
        return new ThreadPoolExecutor(
                4, 16, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                new CustomizableThreadFactory("ai-stream-"),
                // 队列满时由调用线程兜底执行，保证不丢任务
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * AI 上游调用客户端
     *
     * <p>注意：<b>不设置 HttpRequest 级别的 timeout</b> —— JDK 的该超时覆盖整个请求交换
     * （含响应体），会把长流中途截断；这里只设 connectTimeout，长耗时由业务层看门狗控制。</p>
     */
    @Bean
    public HttpClient aiHttpClient(java.util.concurrent.Executor aiExecutor) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(aiExecutor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
