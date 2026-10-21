package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 签到核销单 VO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "签到核销单信息")
public class CheckinVO {

    @Schema(description = "签到单ID")
    private Long id;

    @Schema(description = "核销码")
    private String checkinCode;

    @Schema(description = "预约单ID")
    private Long reservationId;

    @Schema(description = "预约单号")
    private String reservationNo;

    @Schema(description = "实验室ID")
    private Long labId;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "使用人用户ID")
    private Long userId;

    @Schema(description = "使用人姓名")
    private String userName;

    @Schema(description = "预约日期")
    private LocalDate reserveDate;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "状态编码 SIGN_IN_PENDING/CHECKED_IN/COMPLETED/EXPIRED/CANCELLED")
    private String status;

    @Schema(description = "状态中文描述")
    private String statusDesc;

    @Schema(description = "实际签到时间")
    private LocalDateTime checkinTime;

    @Schema(description = "实际签退时间")
    private LocalDateTime checkoutTime;

    @Schema(description = "核销人姓名")
    private String checkinByName;

    @Schema(description = "核销备注")
    private String remark;

    @Schema(description = "当前是否可签到（处于可核销时间窗内）")
    private Boolean checkable;

    @Schema(description = "当前是否可签退")
    private Boolean checkoutable;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
