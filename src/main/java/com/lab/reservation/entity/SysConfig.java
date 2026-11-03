package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置项（键值对）
 *
 * <p>维护模式、定时任务开关、外部通知 webhook 等运行时开关都落在这张表，
 * 修改后无需重启，服务层带 60 秒本地缓存。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型：STRING/NUMBER/BOOLEAN/JSON/SECRET */
    private String valueType;

    /** 分组名称 */
    private String groupName;

    /** 展示名称 */
    private String label;

    /** 说明 */
    private String description;
}
