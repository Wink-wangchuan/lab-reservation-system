package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 知识库条目入参
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "知识库条目")
public class KnowledgeDTO {

    @Schema(description = "主键ID（导入时携带则按 ID 更新）")
    private Long id;

    @Schema(description = "知识分类")
    @NotBlank(message = "知识分类不能为空")
    @Size(max = 50, message = "分类最长 50 个字符")
    private String category;

    @Schema(description = "知识标题")
    @NotBlank(message = "知识标题不能为空")
    @Size(max = 100, message = "标题最长 100 个字符")
    private String title;

    @Schema(description = "触发关键词（英文逗号分隔）")
    @NotBlank(message = "触发关键词不能为空")
    @Size(max = 500, message = "关键词最长 500 个字符")
    private String keywords;

    @Schema(description = "知识正文")
    @NotBlank(message = "知识正文不能为空")
    private String content;

    @Schema(description = "命中排序优先级，越大越靠前")
    private Integer priority;

    @Schema(description = "状态：0停用 1启用")
    private Integer status;
}
