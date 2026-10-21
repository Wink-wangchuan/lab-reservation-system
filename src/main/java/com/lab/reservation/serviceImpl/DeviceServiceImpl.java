package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.OptimisticLockGuard;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.DeviceDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.service.DeviceService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;

import java.util.List;

/**
 * 设备台账服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final LabInfoMapper labInfoMapper;
    /** 设备数量变化会影响驾驶舱「设备总数」统计，写操作后需让统计缓存失效 */
    private final StatisticsService statisticsService;

    @Override
    public IPage<DeviceVO> page(IPage<DeviceVO> page, String deviceType, Long labId,
                                Integer status, Integer faultStatus, String keyword) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .eq(StrUtil.isNotBlank(deviceType), Device::getDeviceType, deviceType)
                .eq(labId != null, Device::getLabId, labId)
                .eq(status != null, Device::getStatus, status)
                .eq(faultStatus != null, Device::getFaultStatus, faultStatus)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Device::getDeviceCode, keyword)
                        .or().like(Device::getDeviceName, keyword)
                        .or().like(Device::getBrandModel, keyword))
                .orderByDesc(Device::getCreateTime);

        Page<Device> entityPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<Device> result = deviceMapper.selectPage(entityPage, wrapper);

        Page<DeviceVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    @Override
    public DeviceVO detail(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return convert(device);
    }

    @Override
    public void add(DeviceDTO dto) {
        // 设备编号唯一性校验
        Long count = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceCode, dto.getDeviceCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "设备编号已存在");
        }
        LabInfo lab = labInfoMapper.selectById(dto.getLabId());
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        Device device = new Device();
        BeanUtils.copyProperties(dto, device);
        device.setLabName(lab.getLabName());
        device.setFaultStatus(0);          // 新增默认无故障
        if (device.getStatus() == null) {
            device.setStatus(1);           // 默认在用
        }
        deviceMapper.insert(device);
        statisticsService.evictStatCache();
    }

    @Override
    public void update(DeviceDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备ID不能为空");
        }
        Device device = deviceMapper.selectById(dto.getId());
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        // 编号冲突校验（排除自身）
        Long count = deviceMapper.selectCount(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceCode, dto.getDeviceCode())
                .ne(Device::getId, dto.getId()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "设备编号已存在");
        }
        BeanUtils.copyProperties(dto, device);
        LabInfo lab = labInfoMapper.selectById(dto.getLabId());
        if (lab != null) {
            device.setLabName(lab.getLabName());
        }
        // 乐观锁：版本冲突时 updateById 返回 0，此处统一转成可读提示，避免静默丢写
        OptimisticLockGuard.check(deviceMapper.updateById(device), "该设备");
        statisticsService.evictStatCache();
    }

    @Override
    public void delete(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (device.getFaultStatus() != null && device.getFaultStatus() == 1) {
            throw new BusinessException(ResultCode.DEVICE_REPAIR_LOCKED,
                    "设备处于故障中，无法删除，请先处理报修");
        }
        deviceMapper.deleteById(id); // 逻辑删除（delete 逻辑由 MP 全局配置接管）
        statisticsService.evictStatCache();
    }

    private DeviceVO convert(Device device) {
        DeviceVO vo = new DeviceVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}