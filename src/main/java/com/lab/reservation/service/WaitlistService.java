package com.lab.reservation.service;

import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.vo.WaitlistVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 候补队列服务
 *
 * @author LabWise
 * @since 3.7.0
 */
public interface WaitlistService {

    /** 加入候补队列（仅当该时段确实已约满时才允许） */
    WaitlistVO join(ReservationSubmitDTO dto);

    /** 我的候补记录 */
    List<WaitlistVO> mine();

    /** 取消自己的候补 */
    void cancel(Long id);

    /** 定时递补：容量释放后按排队顺序自动转成待审核预约 */
    void promoteWaiting();

    /**
     * 容量释放后的即时递补：只处理「该实验室 + 该日期」的等待队列
     *
     * <p>由 {@code LabSeatReleasedEvent} 的事务提交后监听器调用，
     * 让用户取消/被驳回后几秒内就能收到递补结果，而不是等下一个 5 分钟扫描周期。</p>
     */
    void promoteFor(Long labId, LocalDate date);
}
