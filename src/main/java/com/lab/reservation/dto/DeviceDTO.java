package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备新增/编辑入参 DTO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "设备台账请求")
public class DeviceDTO {

    private Long id; // 编辑时传递

    @Schema(description = "设备编号")
    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    @Schema(description = "设备名称")
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "所属实验室ID")
    @NotNull(message = "所属实验室不能为空")
    private Long labId;

    @Schema(description = "购置日期")
    private LocalDate purchaseDate;

    @Schema(description = "品牌型号")
    private String brandModel;

    @Schema(description = "在用状态：0停用 1在用 2报废")
    @Min(value = 0, message = "状态值不合法")
    @Max(value = 2, message = "状态值不合法")
    private Integer status;

    @Schema(description = "数量：该条台账代表的同类设备台/套数，默认 1")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;

    @Schema(description = "购置价格")
    @DecimalMin(value = "0", message = "价格不能为负")
    private BigDecimal price;

    @Schema(description = "备注")
    private String remark;
}