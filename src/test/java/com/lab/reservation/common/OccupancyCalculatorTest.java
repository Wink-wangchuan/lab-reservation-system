package com.lab.reservation.common;

import com.lab.reservation.entity.LabReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 时段占用峰值计算器测试
 *
 * @author LabWise
 * @since 3.4.0
 */
@DisplayName("占用峰值：扫描线算法")
class OccupancyCalculatorTest {

    @Test
    @DisplayName("无预约时峰值为 0")
    void empty() {
        assertEquals(0, OccupancyCalculator.peakPeople(List.of(),
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    @DisplayName("完全重叠的多单按人数相加")
    void fullyOverlapping() {
        assertEquals(30, OccupancyCalculator.peakPeople(
                List.of(reservation(8, 0, 9, 40, 10), reservation(8, 0, 9, 40, 20)),
                LocalTime.of(8, 0), LocalTime.of(9, 40)));
    }

    @Test
    @DisplayName("前后错开的两单取峰值而不是相加")
    void staggeredTakesPeak() {
        // 08:00-09:40 与 09:40-11:20 在 09:00-10:00 内任一时刻都只有一单在场
        assertEquals(20, OccupancyCalculator.peakPeople(
                List.of(reservation(8, 0, 9, 40, 20), reservation(9, 40, 11, 20, 20)),
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    @DisplayName("部分重叠时只统计裁剪到目标区间内的部分")
    void clipped() {
        assertEquals(50, OccupancyCalculator.peakPeople(
                List.of(reservation(8, 0, 10, 0, 30), reservation(9, 0, 12, 0, 20)),
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    @DisplayName("完全不相交的预约被忽略")
    void notIntersecting() {
        assertEquals(0, OccupancyCalculator.peakPeople(
                List.of(reservation(13, 0, 14, 0, 40)),
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    private LabReservation reservation(int sh, int sm, int eh, int em, int people) {
        LabReservation r = new LabReservation();
        r.setStartTime(LocalTime.of(sh, sm));
        r.setEndTime(LocalTime.of(eh, em));
        r.setPeopleCount(people);
        return r;
    }
}
