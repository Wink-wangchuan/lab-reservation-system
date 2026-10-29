package com.lab.reservation.serviceImpl;

import com.lab.reservation.dto.AiActionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 指令解析器测试
 *
 * <p>解析层是纯本地规则、不调用大模型（上游单次响应约 10 秒，放在指令前体验不可接受），
 * 因此这里直接覆盖口语到四类动作的映射、参数抽取与中文数字转换。</p>
 *
 * @author LabWise
 * @since 3.0.0
 */
@DisplayName("AI 指令解析")
class AiIntentParserTest {

    private AiIntentParser parser() {
        return new AiIntentParser();
    }

    @Test
    @DisplayName("操作指令粗筛：命令通过，咨询拦截")
    void looksOperational() {
        AiIntentParser parser = parser();
        assertTrue(parser.looksOperational("帮我预约本周五第一计算机实验室"));
        assertTrue(parser.looksOperational("把张三的预约批了"));
        assertTrue(parser.looksOperational("取消我明天的预约"));
        assertTrue(parser.looksOperational("三号机开不了机，帮我报修"));

        assertFalse(parser.looksOperational("预约规则是什么？"));
        assertFalse(parser.looksOperational("怎么签到和签退"));
        assertFalse(parser.looksOperational("我的预约现在是什么状态"));
        assertFalse(parser.looksOperational("今天天气怎么样"));
    }

    @Test
    @DisplayName("本地关键词解析：识别四类动作与驳回语义")
    void ruleFallback() {
        AiIntentParser parser = parser();
        assertEquals(AiActionPlan.BOOK, parser.parse("我想约明天下午的第一计算机实验室").getAction());
        assertEquals(AiActionPlan.CANCEL, parser.parse("帮我取消明天的预约").getAction());

        AiActionPlan repair = parser.parse("数字示波器开不了机了，帮我报修");
        assertEquals(AiActionPlan.REPAIR, repair.getAction());
        assertEquals("数字示波器开不了机了", repair.getFaultDesc());

        AiActionPlan approve = parser.parse("帮我通过所有待审核的预约");
        assertEquals(AiActionPlan.APPROVE, approve.getAction());
        assertEquals("APPROVE", approve.getDecision());
        assertTrue(approve.getAll());

        AiActionPlan reject = parser.parse("帮我驳回张三的预约");
        assertEquals(AiActionPlan.APPROVE, reject.getAction());
        assertEquals("REJECT", reject.getDecision());
        assertEquals("张三", reject.getTarget());
    }

    @Test
    @DisplayName("中文数字转换")
    void chineseNumbers() {
        assertEquals(Integer.valueOf(2), AiIntentParser.cnNumber("两"));
        assertEquals(Integer.valueOf(8), AiIntentParser.cnNumber("八"));
        assertEquals(Integer.valueOf(10), AiIntentParser.cnNumber("十"));
        assertEquals(Integer.valueOf(12), AiIntentParser.cnNumber("十二"));
        assertEquals(Integer.valueOf(20), AiIntentParser.cnNumber("二十"));
        assertEquals(Integer.valueOf(15), AiIntentParser.cnNumber("15"));
        assertNull(AiIntentParser.cnNumber("abc"));
    }

    @Test
    @DisplayName("从口语里抽出用途与人数")
    void extractParams() {
        AiIntentParser parser = parser();
        AiActionPlan plan = parser.parse("帮我约明天下午一机房写代码，两个人");
        assertEquals(AiActionPlan.BOOK, plan.getAction());
        assertEquals("写代码", plan.getPurpose());
        assertEquals(Integer.valueOf(2), plan.getPeopleCount());
    }
}
