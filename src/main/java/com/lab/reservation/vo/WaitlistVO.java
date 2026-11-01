package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 候补队列 VO
 *
 * @author LabWise
 * @since 3.7.0
 */
@Data
@Schema(description = "候补记录")
public class WaitlistVO {

    private Long id;

    @Schema(description = "实验室ID")
    private Long labId;

    @Schema(description = "实验室名称")
    private String labName;

    @Schema(description = "预约日期")
    private LocalDate reserveDate;

    @Schema(description = "开始时间")
    private LocalTime startTime;

    @Schema(description = "结束时间")
    private LocalTime endTime;

    @Schema(description = "使用人数")
    private Integer peopleCount;

    @Schema(description = "用途说明")
    private String purpose;

    @Schema(description = "状态码")
    private String status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "排队时间")
    private LocalDateTime createTime;
}
