package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 助手提问入参 DTO
 *
 * @author LabWise
 * @since 1.6.0
 */
@Data
@Schema(description = "AI 助手提问")
public class AiChatDTO {

    @Schema(description = "用户提问内容")
    @NotBlank(message = "请输入内容")
    @Size(max = 2000, message = "单条消息不能超过 2000 字")
    private String message;
}
