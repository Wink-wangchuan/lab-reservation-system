package com.lab.reservation.mapper;

import com.lab.reservation.vo.StatsOverviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
                  WHERE deleted = 0 AND status = 'PENDING') AS pendingRepair,
                (SELECT COUNT(*) FROM device
                  WHERE deleted = 0 AND fault_status = 1) AS faultDevice
            """)
    StatsOverviewVO selectOverview();
}
