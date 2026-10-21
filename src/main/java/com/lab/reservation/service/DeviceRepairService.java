package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.DeviceRepairDTO;
import com.lab.reservation.vo.RepairDeviceOptionVO;
import com.lab.reservation.vo.RepairVO;

import java.util.List;

/**
 * 设备报修闭环服务接口
 *
 * <p style="color:#c00"><b>答辩重点：报修闭环</b></p>
 * 提交报修 → 设备标记故障并锁定所属实验室预约；维修完成/取消 → 清除故障并解锁实验室。
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface DeviceRepairService {

    /** 提交报修（自动锁定设备与所属实验室） */
    RepairVO submit(DeviceRepairDTO dto);

    /** 开始维修：PENDING → REPAIRING */
    RepairVO start(Long repairId, String remark);

    /** 完成维修：REPAIRING → COMPLETED，若无其他排障单则解锁设备/实验室 */
    RepairVO complete(Long repairId, String remark);

    /** 取消报修：PENDING/REPAIRING → CANCELLED，解锁设备/实验室 */
    RepairVO cancel(Long repairId, String remark);

    /** 报修单详情 */
    RepairVO detail(Long repairId);

    /** 分页查询全部报修单 */
    IPage<RepairVO> page(IPage<RepairVO> page, Long labId, String status, String keyword);

    /** 分页查询我提交的报修单 */
    IPage<RepairVO> pageMine(IPage<RepairVO> page, String status);

    /**
     * 报修可选设备清单（精简字段）
     *
     * <p>提交报修只需选设备，普通用户无需「设备台账」权限即可获取该清单，
     * 同时避免把资产类字段（价格、购置日期等）暴露出去。</p>
     */
    List<RepairDeviceOptionVO> deviceOptions();
}