package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内消息实体
 *
 * <p>预约审批、报修流转等业务事件由服务层主动推送为个人消息，
 * 形成「事件 → 消息 → 用户」的站内通知中心。</p>
 *
 * @author LabWise
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    /** 接收人用户ID（0 表示全体广播的源记录，由服务层展开为个人消息） */
    private Long userId;

    /** 消息标题 */
    private String title;

    /** 消息正文 */
    private String content;

    /** 消息类型：SYSTEM/RESERVATION/REPAIR */
    private String noticeType;

    /** 关联业务ID（预约单/报修单等），可为空 */
    private Long bizId;

    /** 是否已读：0未读 1已读 */
    private Integer readFlag;

    /** 已读时间 */
    private LocalDateTime readTime;
}
