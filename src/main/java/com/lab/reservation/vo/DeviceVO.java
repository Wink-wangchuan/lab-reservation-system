package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "设备台账信息")
public class DeviceVO {

    private Long id;

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "所属实验室ID")
    private Long labId;

    @Schema(description = "所属实验室名称")
    private String labName;

    @Schema(description = "购置日期")
    private LocalDate purchaseDate;

    @Schema(description = "品牌型号")
    private String brandModel;

    @Schema(description = "在用状态：0停用 1在用 2报废")
    private Integer status;

    @Schema(description = "故障状态：0正常 1故障")
    private Integer faultStatus;

    @Schema(description = "当前故障描述")
    private String faultDesc;

    @Schema(description = "数量：该条台账代表的同类设备台/套数")
    private Integer quantity;

    @Schema(description = "购置价格")
    private BigDecimal price;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}