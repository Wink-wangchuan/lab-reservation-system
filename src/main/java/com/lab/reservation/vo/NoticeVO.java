package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息 VO
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@Schema(description = "站内消息")
public class NoticeVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息正文")
    private String content;

    @Schema(description = "消息类型编码 SYSTEM/RESERVATION/REPAIR")
    private String noticeType;

    @Schema(description = "消息类型中文描述")
    private String noticeTypeDesc;

    @Schema(description = "关联业务ID")
    private Long bizId;

    @Schema(description = "是否已读：0未读 1已读")
    private Integer readFlag;

    @Schema(description = "已读时间")
    private LocalDateTime readTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
