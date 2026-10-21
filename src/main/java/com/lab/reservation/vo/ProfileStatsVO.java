package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 个人中心 · 我的数据统计 VO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "我的数据统计")
public class ProfileStatsVO {

    @Schema(description = "我的预约总数")
    private Long reservationTotal;

    @Schema(description = "待审核预约数")
    private Long pendingReservation;

    @Schema(description = "已通过预约数")
    private Long approvedReservation;

    @Schema(description = "已完成预约数")
    private Long completedReservation;

    @Schema(description = "我的报修总数")
    private Long repairTotal;

    @Schema(description = "未读消息数")
    private Long unreadNotice;
}
