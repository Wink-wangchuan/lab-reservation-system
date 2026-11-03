package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统公告
 *
 * <p>支持有效期（startTime/endTime），到点自动生效/下线；
 * 前端在布局顶部展示生效中的公告横幅。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_announcement")
public class SysAnnouncement extends BaseEntity {

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 级别：INFO/WARNING/URGENT */
    private String level;

    /** 状态：0下线 1生效 */
    private Integer status;

    /** 生效开始时间，空表示立即 */
    private LocalDateTime startTime;

    /** 生效结束时间，空表示长期 */
    private LocalDateTime endTime;
}
