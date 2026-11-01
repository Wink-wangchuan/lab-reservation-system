package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 实验室候补队列实体
 *
 * <p>时段约满时用户可加入候补；有人取消或驳回释放容量后，
 * 定时任务按加入先后顺序自动递补，为排队用户提交待审核预约。</p>
 *
 * @author LabWise
 * @since 3.7.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_waitlist")
public class LabWaitlist extends BaseEntity {

    /** 实验室ID */
    private Long labId;

    /** 实验室名称（冗余快照） */
    private String labName;

    /** 预约日期 */
    private LocalDate reserveDate;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 排队用户ID */
    private Long userId;

    /** 排队用户姓名（冗余快照） */
    private String userName;

    /** 使用人数 */
    private Integer peopleCount;

    /** 用途说明 */
    private String purpose;

    /** 状态：WAITING 等待中 / PROMOTED 已递补 / CANCELLED 已取消 / EXPIRED 已失效 */
    private String status;
}
