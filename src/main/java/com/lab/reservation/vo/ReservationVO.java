package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约详情 VO（对外返回，不暴露内部字段）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "预约单信息")
public class ReservationVO {

    @Schema(description = "预约单ID")
    private Long id;

    @Schema(description = "预约单号")
    private String reservationNo;

    @Schema(description = "实验室ID")
    private Long labId;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "学生用户ID")
    private Long studentId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "审核教师用户ID")
    private Long teacherId;

    @Schema(description = "审核教师姓名")
    private String teacherName;

    @Schema(description = "预约日期")
    private LocalDate reserveDate;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "用途说明")
    private String purpose;

    @Schema(description = "使用人数")
    private Integer peopleCount;

    @Schema(description = "状态编码 DRAFT/PENDING/APPROVED/REJECTED/COMPLETED/CANCELLED")
    private String status;

    @Schema(description = "状态中文描述")
    private String statusDesc;

    @Schema(description = "当前登录人是否可审批该单（教师提交的单仅管理员可批）")
    private Boolean canApprove;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "使用设备ID(逗号分隔)")
    private String useDeviceIds;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "完成时间")
    private LocalDateTime completeTime;
}