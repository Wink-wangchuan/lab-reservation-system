package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.config.AiProperties;
import com.lab.reservation.dto.AiChatDTO;
import com.lab.reservation.exception.AiStreamAbortedException;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.AiAssistantService;
import com.lab.reservation.vo.AiMessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 助手接口
 *
 * <p style="color:#c00"><b>答辩亮点：后端代理 + SSE 流式转发</b></p>
 *
 * <p>安全与稳定性要点：</p>
 * <ul>
 *   <li>令牌保存在服务端环境变量，前端只与本系统通信，不存在令牌泄漏到浏览器的风险</li>
 *   <li><b>userId 必须在请求线程内取出</b>：{@code SecurityContext} 不跨线程，异步线程里取会抛 401</li>
 *   <li>转发交给专用线程池执行，不占用 Tomcat 请求线程（长流会把请求线程耗光）</li>
 *   <li>异常统一通过 {@code error} 事件推给前端，不用 {@code completeWithError}
 *       （响应可能已提交，那样前端收不到结构化的错误）</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.6.0
 */
@Slf4j
@Tag(name = "AI 助手", description = "流式对话与会话历史（服务端代理，令牌不外泄）")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiProperties aiProperties;
    private final ThreadPoolExecutor aiExecutor;

    /**
     * SseEmitter 超时，须小于 spring.mvc.async.request-timeout（400s）
     *
     * <p>流式输出下回答可能持续数分钟，此处按「整条连接最长存活时间」放宽；
     * 真正的异常中断由服务层空闲看门狗（连续无数据）负责，不靠这个超时。</p>
     */
    private static final long EMITTER_TIMEOUT_MS = 360_000L;

    @Operation(summary = "流式对话（text/event-stream，事件名 delta / done / error）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter chat(@Valid @RequestBody AiChatDTO dto, HttpServletResponse response) {
        // 未配置时在创建 emitter 之前抛出，走全局异常处理返回统一 Result（前端按 JSON 分支提示）
        if (!aiProperties.isConfigured()) {
            throw new BusinessException(ResultCode.AI_NOT_CONFIGURED);
        }
        // 请求线程内取好当前用户：异步线程里拿不到 SecurityContext
        Long userId = SecurityUtil.getUserId();
        String question = dto.getMessage().trim();
        // AI 代办会调用 ReservationService 等业务服务，这些服务内部同样依赖 SecurityContext，
        // 因此需要把认证信息显式带到异步线程（SecurityContextHolder 默认是 ThreadLocal，不跨线程）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 打字机效果：Tomcat 响应缓冲默认 8KB，逐段增量会被攒批后一次性蹦出，故调至最小
        response.setBufferSize(1);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        aiExecutor.execute(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            try {
                aiAssistantService.streamChat(userId, question, delta -> sendDelta(emitter, delta));
                sendQuietly(emitter, "done", "");
                emitter.complete();
            } catch (AiStreamAbortedException e) {
                // 客户端已断开（停止生成 / 关闭页面），无需再推送，直接收尾释放上游连接
                emitter.complete();
            } catch (BusinessException e) {
                sendQuietly(emitter, "error", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.error("[AI] 流式对话异常", e);
                sendQuietly(emitter, "error", ResultCode.AI_UPSTREAM_ERROR.getMessage());
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }

    @Operation(summary = "获取最近会话（Redis 缓存）")
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AiMessageVO>> history() {
        return Result.success(aiAssistantService.history());
    }

    @Operation(summary = "清空会话")
    @DeleteMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> clearHistory() {
        aiAssistantService.clearHistory();
        return Result.success("会话已清空", null);
    }

    /* ==================== SSE 推送 ==================== */

    /**
     * 推送正文增量；发送失败（客户端断开）时抛出中止信号，让上游读取循环尽快退出
     */
    private void sendDelta(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().name("delta")
                    .data(Map.of("text", text), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new AiStreamAbortedException();
        }
    }

    /**
     * 推送控制事件（done / error）：此时连接可能已关闭，失败时静默忽略
     */
    private void sendQuietly(SseEmitter emitter, String event, String message) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(Map.of("text", message), MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 连接已关闭，无需处理
        }
    }
}
