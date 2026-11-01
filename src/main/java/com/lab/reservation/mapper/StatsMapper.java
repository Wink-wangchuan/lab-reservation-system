package com.lab.reservation.mapper;

import com.lab.reservation.vo.StatsMetricsVO;
import com.lab.reservation.vo.StatsOverviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱概览统计 Mapper
 *
 * <p>把概览页需要的 8 项指标合并到一次查询：原实现分别调用 8 次 count，
 * 即使有 Redis 缓存，缓存失效时仍要经历 8 个数据库往返。合并后用标量子查询
 * 一次取回全部指标，往返次数降为 1。</p>
 *
 * <p>注意：本类是手写 SQL，MyBatis-Plus 的逻辑删除不会自动追加，
 * 因此每处都必须显式写 {@code deleted = 0}。</p>
 *
 * @author LabWise
 * @since 2.4.0
 */
@Mapper
public interface StatsMapper {

    /**
     * 一次查询取回驾驶舱全部概览指标
     *
     * @return 概览指标（各计数项均不会为 null）
     */
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM lab_info
                  WHERE deleted = 0) AS labTotal,
                (SELECT IFNULL(SUM(quantity), 0) FROM device
                  WHERE deleted = 0) AS deviceTotal,
                (SELECT COUNT(*) FROM sys_user
                  WHERE deleted = 0) AS userTotal,
                (SELECT COUNT(*) FROM lab_reservation
                  WHERE deleted = 0) AS reservationTotal,
                (SELECT COUNT(*) FROM lab_reservation
                  WHERE deleted = 0
                    AND reserve_date = CURDATE()
                    AND status NOT IN ('CANCELLED', 'REJECTED')) AS todayReservation,
                (SELECT COUNT(*) FROM lab_reservation
                  WHERE deleted = 0 AND status = 'PENDING') AS pendingApproval,
                (SELECT COUNT(*) FROM device_repair
                  WHERE deleted = 0 AND status IN ('PENDING', 'REPAIRING')) AS pendingRepair,
                (SELECT COUNT(*) FROM device
                  WHERE deleted = 0 AND fault_status = 1) AS faultDevice
            """)
    StatsOverviewVO selectOverview();

    /**
     * 履约质量指标：同样合并为一次查询，避免缓存失效时多次往返。
     *
     * <p>平均审批时长取「审批动作时间 - 预约创建时间」，只统计通过动作；
     * 驳回同样有响应时长，但业务更关心通过单的流转效率，故分开时只看 APPROVE。</p>
     */
    @Select("""
            SELECT
                (SELECT COUNT(*) FROM lab_reservation
                  WHERE deleted = 0 AND status IN ('APPROVED', 'COMPLETED')) AS approvedTotal,
                (SELECT COUNT(*) FROM lab_reservation
                  WHERE deleted = 0 AND status = 'REJECTED') AS rejectedTotal,
                (SELECT COUNT(*) FROM lab_checkin
                  WHERE deleted = 0 AND status = 'EXPIRED') AS noShowTotal,
                (SELECT COUNT(*) FROM lab_checkin
                  WHERE deleted = 0 AND status IN ('CHECKED_IN', 'COMPLETED', 'EXPIRED')) AS checkinTotal,
                (SELECT IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, r.create_time, a.approve_time)), 1), 0)
                   FROM lab_approval a
                   JOIN lab_reservation r ON r.id = a.reservation_id
                  WHERE a.action = 'APPROVE') AS avgApprovalMinutes
            """)
    StatsMetricsVO selectQuality();

    /**
     * 使用率报表 - 实验室维度：有效预约的场次、使用人数与累计使用时长（分钟）
     */
    @Select("""
            SELECT lab_id AS labId,
                   lab_name AS labName,
                   COUNT(*) AS total,
                   SUM(people_count) AS people,
                   SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time)) AS minutes
              FROM lab_reservation
             WHERE deleted = 0
               AND status IN ('APPROVED', 'COMPLETED')
               AND reserve_date >= #{start}
             GROUP BY lab_id, lab_name
             ORDER BY total DESC
            """)
    List<Map<String, Object>> selectLabUsage(@Param("start") LocalDate start);

    /**
     * 使用率报表 - 时段维度：按时间区间聚合，反映哪些时段最抢手
     */
    @Select("""
            SELECT TIME_FORMAT(start_time, '%H:%i') AS startTime,
                   TIME_FORMAT(end_time, '%H:%i') AS endTime,
                   COUNT(*) AS total,
                   SUM(people_count) AS people
              FROM lab_reservation
             WHERE deleted = 0
               AND status IN ('APPROVED', 'COMPLETED')
               AND reserve_date >= #{start}
             GROUP BY start_time, end_time
             ORDER BY start_time
            """)
    List<Map<String, Object>> selectSlotUsage(@Param("start") LocalDate start);

    /**
     * 使用率报表 - 审批人维度：通过/驳回数量与平均响应时长
     */
    @Select("""
            SELECT teacher_id AS teacherId,
                   teacher_name AS teacherName,
                   SUM(CASE WHEN status IN ('APPROVED', 'COMPLETED') THEN 1 ELSE 0 END) AS approved,
                   SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
                   COUNT(*) AS total,
                   IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, create_time,
                       (SELECT MIN(a.approve_time) FROM lab_approval a
                         WHERE a.reservation_id = lab_reservation.id))), 1), 0) AS avgMinutes
              FROM lab_reservation
             WHERE deleted = 0
               AND teacher_id IS NOT NULL
               AND reserve_date >= #{start}
             GROUP BY teacher_id, teacher_name
             ORDER BY total DESC
            """)
    List<Map<String, Object>> selectTeacherUsage(@Param("start") LocalDate start);
}
