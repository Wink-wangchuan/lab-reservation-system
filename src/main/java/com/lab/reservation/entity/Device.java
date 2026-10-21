package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账实体
 *
 * <p style="color:#c00"><b>答辩亮点：</b>faultStatus 为 1 表示设备故障，
 * 设备故障将触发{@code 设备故障状态锁定实验室预约}——即该设备所属实验室禁止再被预约。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device")
public class Device extends BaseEntity {

    /** 乐观锁版本号：管理端台账类实体启用，防止多人并发维护互相覆盖 */
    @Version
    private Integer version;

    /** 设备编号 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型 */
    private String deviceType;

    /** 所属实验室ID */
    private Long labId;

    /** 所属实验室名称（冗余快照） */
    private String labName;

    /** 购置日期（Java 25 LocalDate） */
    private LocalDate purchaseDate;

    /** 品牌型号 */
    private String brandModel;

    /** 在用状态：0停用 1在用 2报废 */
    private Integer status;

    /** 故障状态：0正常 1故障（故障期间锁定实验室） */
    private Integer faultStatus;

    /** 当前故障描述 */
    private String faultDesc;

    /**
     * 数量：该条台账代表同类设备的台/套数
     *
     * <p>台账按「实验室的设备配置」登记（而非逐台一行），例如「教学台式机 联想 ThinkCentre M760 60 台」
     * 记一条，设备编号字段标注覆盖的编号区间（DEV-101-001 ~ DEV-101-060）。
     * 驾驶舱「设备总数」按本字段汇总，得到实际台数。</p>
     */
    private Integer quantity;

    /** 购置价格 */
    private BigDecimal price;

    /** 备注 */
    private String remark;
}