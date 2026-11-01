package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 实验室闭馆日历实体
 *
 * <p>按日期区间覆盖按星期的开放时段，用于节假日、考试周、临时维护等场景。
 * {@code labId = 0} 表示全部实验室停用。</p>
 *
 * @author LabWise
 * @since 3.7.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_closure")
public class LabClosure extends BaseEntity {

    /** 实验室ID；0 表示全部实验室 */
    private Long labId;

    /** 闭馆开始日期 */
    private LocalDate startDate;

    /** 闭馆结束日期（含） */
    private LocalDate endDate;

    /** 闭馆原因 */
    private String reason;
}
