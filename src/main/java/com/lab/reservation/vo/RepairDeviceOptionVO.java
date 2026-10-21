package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 报修可选设备清单 VO（精简字段）
 *
 * <p>提交报修时只需从设备清单里选一台故障设备，无需看到价格、购置日期、品牌型号等台账信息。
 * 单独定义精简 VO 而非直接复用 {@code DeviceVO}，是为了在「学生也能提交报修」的前提下
 * 避免把资产类字段暴露给普通用户（最小权限原则）。</p>
 *
 * @author LabWise
 * @since 1.4.0
 */
@Data
@Schema(description = "报修可选设备")
public class RepairDeviceOptionVO {

    @Schema(description = "设备ID")
    private Long id;

    @Schema(description = "设备编号")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "所属实验室ID")
    private Long labId;

    @Schema(description = "所属实验室名称")
    private String labName;

    @Schema(description = "故障状态：0正常 1故障（前端据此禁用该项）")
    private Integer faultStatus;
}
