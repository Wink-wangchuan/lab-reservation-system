package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.CheckinVerifyDTO;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.vo.CheckinVO;

/**
 * 预约签到核销服务接口
 *
 * <p style="color:#c00"><b>答辩亮点：预约业务闭环</b></p>
 * 审批通过 → 生成核销码 → 到场核销签到 → 离场签退完成，
 * 并由定时任务推进「超时未签到 = 已过期」「已签到超时 = 已完成」。
 *
 * @author LabWise
 * @since 1.1.0
 */
public interface CheckinService {

    /**
     * 预约审批通过后为其生成签到凭证（核销码）
     */
    void createForReservation(LabReservation reservation);

    /**
     * 获取（必要时补建）指定预约单的核销码，仅本人或具备核销权限者可见
     */
    CheckinVO codeOf(Long reservationId);

    /**
     * 核销签到：按核销码定位签到单，校验时间窗后置为已签到
     */
    CheckinVO verify(CheckinVerifyDTO dto);

    /**
     * 签退：已签到单据置为已完成
     */
    CheckinVO checkout(Long checkinId, String remark);

    /**
     * 预约被取消时联动置签到单为已取消
     */
    void cancelByReservation(Long reservationId, String reason);

    /**
     * 我的签到记录分页
     */
    IPage<CheckinVO> pageMine(IPage<CheckinVO> page, String status);

    /**
     * 全部签到记录分页（管理端）
     */
    IPage<CheckinVO> pageAll(IPage<CheckinVO> page, String status, String keyword);

    /**
     * 签到单详情
     */
    CheckinVO detail(Long checkinId);
}
