package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.vo.ReservationVO;

/**
 * 预约业务服务接口
 *
 * <p style="color:#c00"><b>答辩重点：</b>时间冲突校验、预约状态机流转、
 * 设备故障锁定实验室、多级审批流水。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface ReservationService {

    /**
     * 提交预约（学生），自动进入 PENDING 待审核状态
     */
    ReservationVO submit(ReservationSubmitDTO dto);

    /**
     * 取消预约（本人，草稿/待审核/已通过 可取消）
     */
    void cancel(Long reservationId, String reason);

    /**
     * 审批预约（教师）：APPROVE通过 / REJECT驳回，写入审批流水
     */
    ReservationVO approve(ApprovalDTO dto);

    /**
     * 分页查询我的预约（当前登录用户）
     */
    IPage<ReservationVO> pageMyReservations(IPage<ReservationVO> page, String status);

    /**
     * 分页查询全部预约（管理员/教师按条件筛选）
     */
    IPage<ReservationVO> pageAll(IPage<ReservationVO> page, Long labId,
                                 String status, String keyword);

    /**
     * 查询预约详情
     */
    ReservationVO getDetail(Long reservationId);

    /**
     * 预约记录导出（POI Excel），筛选条件复用管理端列表
     */
    void exportExcel(jakarta.servlet.http.HttpServletResponse response,
                     Long labId, String status, String keyword);
}