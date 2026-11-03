package com.lab.reservation.service;

import com.lab.reservation.vo.WeekScheduleVO;

import java.time.LocalDate;

/**
 * 实验室周日程服务
 *
 * @author LabWise
 * @since 4.6.0
 */
public interface LabScheduleService {

    /**
     * 查询某实验室某一周的日程
     *
     * @param labId     实验室ID
     * @param startDate 周起始日期（为空取本周一）
     */
    WeekScheduleVO week(Long labId, LocalDate startDate);
}
