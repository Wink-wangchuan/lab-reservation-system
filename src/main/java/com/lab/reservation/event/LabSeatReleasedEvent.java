package com.lab.reservation.event;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 实验室容量释放事件
 *
 * <p style="color:#c00"><b>答辩亮点：</b>把「取消/驳回/改期导致容量空出来」这件事从写操作里解耦出去。
 * 写事务只负责发布事件，候补递补在事务提交后异步执行，
 * 这样既能做到「几乎实时递补」，又不会因为递补失败影响取消/驳回本身。</p>
 *
 * @author LabWise
 * @since 4.0.0
 */
@Getter
public class LabSeatReleasedEvent {

    /** 释放容量的实验室 */
    private final Long labId;

    /** 释放的是哪一天的容量 */
    private final LocalDate date;

    /** 触发原因（仅用于日志排查） */
    private final String reason;

    public LabSeatReleasedEvent(Long labId, LocalDate date, String reason) {
        this.labId = labId;
        this.date = date;
        this.reason = reason;
    }
}
