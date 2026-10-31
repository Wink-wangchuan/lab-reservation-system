package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.LabInfoDTO;
import com.lab.reservation.vo.LabInfoVO;

import java.util.List;

/**
 * 实验室信息服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface LabInfoService {

    /** 实验室分页（管理端，多条件） */
    IPage<LabInfoVO> page(IPage<LabInfoVO> page, String labType, String labName,
                          Integer openStatus, String keyword);

    /** 全部在用实验室（下拉选择用） */
    List<LabInfoVO> listAll();

    /** 实验室详情（含故障锁定状态与今日预约数） */
    LabInfoVO detail(Long id);

    /** 新增实验室 */
    void add(LabInfoDTO dto);

    /** 更新实验室 */
    void update(LabInfoDTO dto);

    /** 删除实验室（校验存在预约则阻止） */
    void delete(Long id);

    /** 开放/关闭预约开关 */
    void toggleOpen(Long id, Integer openStatus);

    /** 启用/禁止预约（管控） */
    void toggleReserve(Long id, Integer enableReserve);

    /** 切换时段占用模式：1可多人共享 0整间独占 */
    void toggleShareMode(Long id, Integer shareMode);
}