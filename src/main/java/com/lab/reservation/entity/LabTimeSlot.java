package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 实验室开放时间段实体
 *
 * <p>核心业务字段 startTime / endTime 使用 {@link LocalTime}（Java 25 新时间 API），
 * 用于预约时段的合法性校验与时间冲突算法。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_time_slot")
public class LabTimeSlot extends BaseEntity {

    /** 实验室ID */
    private Long labId;

    /** 星期：1-7（周一至周日） */
    private Integer weekDay;

    /** 时间段名称（如 上午第一节） */
    private String slotName;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 该时段可预约人数上限（0 不限） */
    private Integer maxPeople;

    /** 状态：0停用 1启用 */
    private Integer status;
}