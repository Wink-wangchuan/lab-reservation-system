package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.DeviceDTO;
import com.lab.reservation.vo.DeviceVO;

/**
 * 设备台账服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface DeviceService {

    /** 分页查询设备台账 */
    IPage<DeviceVO> page(IPage<DeviceVO> page, String deviceType, Long labId,
                         Integer status, Integer faultStatus, String keyword);

    /** 设备详情 */
    DeviceVO detail(Long id);

    /** 新增设备 */
    void add(DeviceDTO dto);

    /** 更新设备 */
    void update(DeviceDTO dto);

    /** 删除设备（逻辑删除） */
    void delete(Long id);
}