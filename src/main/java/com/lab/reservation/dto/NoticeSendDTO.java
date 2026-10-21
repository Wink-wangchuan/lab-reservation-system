package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 站内消息发送入参 DTO
 *
 * <p>收件人三选一：指定 userIds 定向发送；userId 为空且 userIds 为空时视为全体广播。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "发送站内消息请求")
public class NoticeSendDTO {

    @Schema(description = "定向接收人用户ID")
    private Long userId;

    @Schema(description = "批量接收人用户ID集合")
    private List<Long> userIds;

    @Schema(description = "消息标题")
    @NotBlank(message = "消息标题不能为空")
    @Size(max = 128, message = "消息标题不能超过128字")
    private String title;

    @Schema(description = "消息正文")
    @NotBlank(message = "消息正文不能为空")
    @Size(max = 1024, message = "消息正文不能超过1024字")
    private String content;

    @Schema(description = "消息类型 SYSTEM/RESERVATION/REPAIR")
    private String noticeType;

    @Schema(description = "关联业务ID")
    private Long bizId;
}
