package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 实验室实时占用看板 · 单间实验室卡片
 *
 * <p>状态由「当前时间是否落在某条有效预约时段内」实时推导，
 * 并结合故障锁定与开放开关，给出 IDLE / IN_USE / RESERVED / LOCKED / CLOSED 五态。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "实验室实时占用卡片")
public class LabBoardVO {

    @Schema(description = "实验室ID")
    private Long labId;

    @Schema(description = "实验室编号")
    private String labCode;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "实验室类型")
    private String labType;

    @Schema(description = "所在位置")
    private String location;

    @Schema(description = "容纳人数")
    private Integer capacity;

    @Schema(description = "占用状态 IDLE空闲 / IN_USE使用中 / RESERVED已预约 / LOCKED故障锁定 / CLOSED未开放")
    private String status;

    @Schema(description = "占用状态中文描述")
    private String statusDesc;

    @Schema(description = "当前/最近一条预约单号")
    private String currentReservationNo;

    @Schema(description = "当前使用人")
    private String currentUserName;

    @Schema(description = "当前/最近一条预约的日期（「已预约」状态下可能是未来日期）")
    private LocalDate currentReserveDate;

    @Schema(description = "当前占用时段开始")
    private LocalTime currentStartTime;

    @Schema(description = "当前占用时段结束")
    private LocalTime currentEndTime;

    @Schema(description = "用途说明")
    private String purpose;

    @Schema(description = "今日有效预约数")
    private Long todayCount;

    @Schema(description = "今日后续待开始的预约数（不含未来日期）")
    private Long upcomingCount;

    @Schema(description = "下一次未来日期的已通过预约日期（仅提示，不影响今日占用状态）")
    private LocalDate nextReserveDate;

    @Schema(description = "下一次未来预约的开始时间")
    private LocalTime nextStartTime;

    @Schema(description = "下一次未来预约的结束时间")
    private LocalTime nextEndTime;

    @Schema(description = "未来日期已通过预约的场次数（不含今天）")
    private Long futureCount;
}
