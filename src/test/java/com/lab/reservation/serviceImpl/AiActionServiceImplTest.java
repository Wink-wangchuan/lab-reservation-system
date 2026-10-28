package com.lab.reservation.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AI 代办日期解析测试
 *
 * <p>日期解析错一天，AI 就会帮用户约错日期，且错误单据还要人工取消，
 * 因此这里锁定各种口语表达（相对日、周几、月日、ISO）的解析结果。</p>
 *
 * @author LabWise
 * @since 3.0.0
 */
@DisplayName("AI 代办日期解析")
class AiActionServiceImplTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Test
    @DisplayName("今天/明天/后天/大后天")
    void relativeDays() {
        assertEquals(TODAY, AiActionServiceImpl.parseDate("帮我预约今天下午的实验室"));
        assertEquals(TODAY.plusDays(1), AiActionServiceImpl.parseDate("帮我预约明天第一计算机实验室"));
        assertEquals(TODAY.plusDays(2), AiActionServiceImpl.parseDate("帮我预约后天的实验室"));
        assertEquals(TODAY.plusDays(3), AiActionServiceImpl.parseDate("帮我预约大后天的实验室"));
    }

    @Test
    @DisplayName("本周几 / 下周几")
    void weekDays() {
        LocalDate monday = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(monday.plusDays(4), AiActionServiceImpl.parseDate("帮我预约本周五第一计算机实验室"));
        assertEquals(monday.plusDays(4 + 7), AiActionServiceImpl.parseDate("帮我预约下周五第一计算机实验室"));
        assertEquals(monday.plusDays(1 + 7), AiActionServiceImpl.parseDate("帮我预约下周二第一计算机实验室"));
    }

    @Test
    @DisplayName("省略本周/下周时：本周已过则取下一周")
    void bareWeekDay() {
        LocalDate monday = TODAY.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thisFriday = monday.plusDays(4);
        LocalDate expected = thisFriday.isBefore(TODAY) ? thisFriday.plusWeeks(1) : thisFriday;
        assertEquals(expected, AiActionServiceImpl.parseDate("帮我预约周五第一计算机实验室"));
    }

    @Test
    @DisplayName("月日表达：已过则顺延到明年")
    void monthDay() {
        LocalDate future = TODAY.plusDays(10);
        String text = "帮我预约" + future.getMonthValue() + "月" + future.getDayOfMonth() + "日第一计算机实验室";
        assertEquals(future, AiActionServiceImpl.parseDate(text));

        LocalDate past = TODAY.minusDays(10);
        String pastText = "帮我预约" + past.getMonthValue() + "月" + past.getDayOfMonth() + "日第一计算机实验室";
        assertEquals(past.plusYears(1), AiActionServiceImpl.parseDate(pastText));
    }

    @Test
    @DisplayName("ISO 日期")
    void isoDate() {
        String text = "帮我预约" + TODAY.plusDays(5).format(DateTimeFormatter.ISO_DATE) + "第一计算机实验室";
        assertEquals(TODAY.plusDays(5), AiActionServiceImpl.parseDate(text));
    }

    @Test
    @DisplayName("无法识别或非法日期返回 null")
    void invalid() {
        assertNull(AiActionServiceImpl.parseDate("帮我预约第一计算机实验室"));
        assertNull(AiActionServiceImpl.parseDate("帮我预约2026-02-30第一计算机实验室"));
    }
}
