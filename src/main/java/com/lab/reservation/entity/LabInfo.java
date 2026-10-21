package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实验室实体
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_info")
public class LabInfo extends BaseEntity {

    /** 乐观锁版本号：管理端台账类实体启用，防止多人并发维护互相覆盖 */
    @Version
    private Integer version;

    /** 实验室编号（如 LAB-A-101） */
    private String labCode;

    /** 实验室名称 */
    private String labName;

    /** 实验室类型（计算机/物理/化学/电子） */
    private String labType;

    /** 所在位置 */
    private String location;

    /** 容量（座位数） */
    private Integer capacity;

    /** 设备配置说明 */
    private String equipmentDesc;

    /** 负责的实验室管理员ID */
    private Long managerId;

    /** 预约开关：0关闭 1开放 */
    private Integer openStatus;

    /** 是否可预约：1可预约 0禁止预约（管控） */
    private Integer enableReserve;

    /** 封面图 */
    private String coverUrl;

    /** 备注 */
    private String remark;
}