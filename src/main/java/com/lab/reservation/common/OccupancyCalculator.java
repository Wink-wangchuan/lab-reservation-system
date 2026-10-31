package com.lab.reservation.common;

import com.lab.reservation.entity.LabReservation;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 时段占用计算器
 *
 * <p style="color:#c00"><b>答辩亮点：同一时段按人数共享</b></p>
 * 预约不再独占整间实验室，而是按人数占用容量。判断「还能约几个人」时，
 * 需要求目标区间内已有预约的<b>并发人数峰值</b>，而不是简单相加——
 * 08:00-10:00 与 10:00-12:00 两单虽然都与 09:00-11:00 相交，但任一时刻只有一单在场。
 *
 * <p>实现方式为扫描线：把每条与目标区间相交的预约裁剪到目标区间内，
 * 在起止时刻各记一个 +人数 / -人数 的增量，按时间顺序累加，最大值即峰值。</p>
 *
 * @author LabWise
 * @since 3.4.0
 */
public final class OccupancyCalculator {

    private OccupancyCalculator() {
    }

    /**
     * 计算目标区间内已有预约的并发人数峰值
     *
     * @param reservations 与目标区间相交的预约（允许包含不相交的，内部会过滤）
     * @param start        目标区间开始
     * @param end          目标区间结束
     * @return 峰值人数，无可计入数据时返回 0
     */
    public static int peakPeople(List<LabReservation> reservations, LocalTime start, LocalTime end) {
        if (reservations == null || reservations.isEmpty() || start == null || end == null) {
            return 0;
        }
        Map<LocalTime, Integer> deltas = new TreeMap<>();
        for (LabReservation reservation : reservations) {
            LocalTime resStart = reservation.getStartTime();
            LocalTime resEnd = reservation.getEndTime();
            if (resStart == null || resEnd == null) {
                continue;
            }
            int people = reservation.getPeopleCount() == null ? 0 : reservation.getPeopleCount();
            // 半开区间 [resStart, resEnd)：与目标区间不相交则直接跳过
            if (people <= 0 || !resStart.isBefore(end) || !resEnd.isAfter(start)) {
                continue;
            }
            LocalTime clippedStart = resStart.isAfter(start) ? resStart : start;
            LocalTime clippedEnd = resEnd.isBefore(end) ? resEnd : end;
            if (clippedStart.isBefore(clippedEnd)) {
                deltas.merge(clippedStart, people, Integer::sum);
                deltas.merge(clippedEnd, -people, Integer::sum);
            }
        }
        int current = 0;
        int peak = 0;
        for (int delta : deltas.values()) {
            current += delta;
            peak = Math.max(peak, current);
        }
        return peak;
    }
}
