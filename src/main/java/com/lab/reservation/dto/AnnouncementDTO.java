package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告入参
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "系统公告")
public class AnnouncementDTO {

    @Schema(description = "标题")
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 150, message = "标题最长 150 个字符")
    private String title;

    @Schema(description = "正文")
    @NotBlank(message = "公告正文不能为空")
    private String content;

    @Schema(description = "级别 INFO/WARNING/URGENT")
    private String level;

    @Schema(description = "状态 0下线 1生效")
    private Integer status;

    @Schema(description = "生效开始时间，空表示立即")
    private LocalDateTime startTime;

    @Schema(description = "生效结束时间，空表示长期")
    private LocalDateTime endTime;
}
