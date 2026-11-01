package com.lab.reservation.service;

import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.vo.WaitlistVO;

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
}
