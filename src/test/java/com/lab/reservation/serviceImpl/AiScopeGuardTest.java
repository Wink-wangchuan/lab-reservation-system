package com.lab.reservation.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 助手越界请求识别测试
 *
 * <p>助手只解答本系统相关问题，写代码/编程作业这类请求必须在进入模型前被拒，
 * 同时不能误伤「去机房写代码」「Java 课怎么预约」这类正常的系统提问。</p>
 *
 * @author LabWise
 * @since 3.6.0
 */
@DisplayName("AI 助手：越界请求识别")
class AiScopeGuardTest {

    @Test
    @DisplayName("编程练习题被识别为越界")
    void codingExercise() {
        assertTrue(AiAssistantServiceImpl.isOffTopicCodingRequest(
                "你可以帮我写一个九九乘法表的python程序吗？"));
        assertTrue(AiAssistantServiceImpl.isOffTopicCodingRequest("帮我写个冒泡排序"));
        assertTrue(AiAssistantServiceImpl.isOffTopicCodingRequest("用 python 实现一个爬虫"));
    }

    @Test
    @DisplayName("明确的写代码话术被识别为越界")
    void codingRequest() {
        assertTrue(AiAssistantServiceImpl.isOffTopicCodingRequest("给我一段快速排序的代码示例"));
        assertTrue(AiAssistantServiceImpl.isOffTopicCodingRequest("帮我写代码"));
    }

    @Test
    @DisplayName("系统相关提问不会被误判")
    void systemQuestions() {
        assertFalse(AiAssistantServiceImpl.isOffTopicCodingRequest("明天下午第一机房还有多少空位？"));
        assertFalse(AiAssistantServiceImpl.isOffTopicCodingRequest("预约时间冲突是怎么判定的？"));
        assertFalse(AiAssistantServiceImpl.isOffTopicCodingRequest("Java 课要用的实验室怎么预约？"));
        assertFalse(AiAssistantServiceImpl.isOffTopicCodingRequest("去机房写代码需要提前预约吗？"));
        assertFalse(AiAssistantServiceImpl.isOffTopicCodingRequest("帮我预约明天下午去一机房写代码"));
    }
}
