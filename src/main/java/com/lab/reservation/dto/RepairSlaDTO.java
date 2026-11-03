package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报修 SLA 设置：优先级 / 指派处理人 / 预计完成时间
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "报修 SLA 设置")
public class RepairSlaDTO {

    @Schema(description = "优先级 LOW/NORMAL/HIGH/URGENT")
    private String priority;

    @Schema(description = "指派处理人ID（传 0 表示取消指派）")
    private Long assigneeId;

    @Schema(description = "预计完成时间，为空则按优先级自动计算")
    private LocalDateTime expectedFinishTime;
}
