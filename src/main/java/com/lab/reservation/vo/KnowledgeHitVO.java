package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识库命中测试结果：返回命中的条目与命中关键词个数
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "知识库命中结果")
public class KnowledgeHitVO {

    @Schema(description = "条目ID")
    private Long id;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "命中关键词个数")
    private Integer score;

    @Schema(description = "正文摘要（截取前 200 字）")
    private String content;
}
