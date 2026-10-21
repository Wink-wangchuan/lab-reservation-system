package com.lab.reservation.service;

import com.lab.reservation.vo.AiMessageVO;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 助手服务接口
 *
 * <p style="color:#c00"><b>答辩亮点：</b>后端代理 + 流式转发 + 系统数据上下文注入。</p>
 * <ul>
 *   <li>令牌只存在服务端环境变量，前端只与本系统接口通信</li>
 *   <li>按提问内容与当前用户身份，选择性注入其有权看到的数据</li>
 *   <li>逐段回调增量内容，由 Controller 通过 SSE 转发给前端</li>
 *   <li>会话历史缓存于 Redis（List），不新建数据表</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.6.0
 */
public interface AiAssistantService {

    /** 当前用户最近会话（按时间正序） */
    List<AiMessageVO> history();

    /** 清空当前用户会话 */
    void clearHistory();

    /**
     * 流式对话
     *
     * @param userId  当前用户ID（必须由 Controller 线程取好传入——SecurityContext 不跨线程）
     * @param question 用户提问
     * @param onDelta 每收到一段增量内容时回调
     * @return 本次回答的完整文本
     */
    String streamChat(Long userId, String question, Consumer<String> onDelta);
}
