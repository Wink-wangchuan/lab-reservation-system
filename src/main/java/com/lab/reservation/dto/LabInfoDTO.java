package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实验室新增/编辑入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "实验室请求")
public class LabInfoDTO {

    private Long id; // 编辑时传递

    @Schema(description = "实验室名称")
    @NotBlank(message = "实验室名称不能为空")
    private String labName;

    @Schema(description = "实验室编号")
    @NotBlank(message = "实验室编号不能为空")
    private String labCode;

    @Schema(description = "实验室类型")
    private String labType;

    @Schema(description = "所在位置")
    private String location;

    @Schema(description = "容纳人数")
    @NotNull(message = "容纳人数不能为空")
    private Integer capacity;

    @Schema(description = "设备配置说明")
    private String equipmentDesc;

    @Schema(description = "负责的实验室管理员ID")
    private Long managerId;

    @Schema(description = "封面图URL")
    private String coverUrl;

    @Schema(description = "备注")
    private String remark;
}