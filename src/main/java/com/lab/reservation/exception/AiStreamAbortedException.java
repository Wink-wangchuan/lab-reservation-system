package com.lab.reservation.exception;

import java.io.Serial;

/**
 * AI 流式回答被客户端中断
 *
 * <p>前端点击「停止生成」或直接关闭页面时，SSE 推送会抛 {@code IOException}。
 * 该异常从此处抛出，用于中断上游模型的读取循环并及时释放连接，
 * 而不是让后台继续空跑消耗令牌。</p>
 *
 * @author LabWise
 * @since 1.6.0
 */
public class AiStreamAbortedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AiStreamAbortedException() {
        super("AI 回答已被客户端中止");
    }
}
