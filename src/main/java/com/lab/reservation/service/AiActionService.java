package com.lab.reservation.service;

/**
 * AI 助手的「代办操作」能力
 *
 * <p>当用户用祈使句表达明确诉求（帮我预约 / 帮我审批）时，由本服务解析并直接调用业务服务执行，
 * 再把结果整理成自然语言返回，避免模型自由发挥造成误操作。</p>
 *
 * @author LabWise
 * @since 3.0.0
 */
public interface AiActionService {

    /**
     * 执行结果
     *
     * @param matched 是否识别为操作指令；false 时交回普通问答流程
     * @param report  面向用户的执行结果（Markdown）
     */
    record Result(boolean matched, String report) {
    }

    /**
     * 尝试把用户输入当作操作指令执行
     *
     * @param question 用户原始输入
     * @return 命中指令时返回执行结果；否则 matched=false
     */
    Result handle(String question);
}
