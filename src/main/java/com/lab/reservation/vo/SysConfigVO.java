package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置项 VO
 *
 * <p>SECRET 类型不回传真实值，只回传 configured 标记，
 * 避免 webhook 地址、密钥等内容在管理页面上被旁窥。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "系统配置项")
public class SysConfigVO {

    private Long id;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值（SECRET 类型为空）")
    private String configValue;

    @Schema(description = "值类型 STRING/NUMBER/BOOLEAN/JSON/SECRET")
    private String valueType;

    @Schema(description = "分组")
    private String groupName;

    @Schema(description = "展示名称")
    private String label;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "SECRET 类型是否已配置")
    private Boolean configured;

    private LocalDateTime updateTime;
}
