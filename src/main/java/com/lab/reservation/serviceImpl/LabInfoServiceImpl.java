package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.OptimisticLockGuard;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.LabInfoDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.service.LabInfoService;
import com.lab.reservation.vo.LabInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.util.List;

/**
 * 实验室信息服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：</b>详情中实时计算<b>故障锁定状态</b>
 * {@code deviceFault}（设备台账是否含故障设备）与<b>今日预约数</b>，
 * 供前端做实验室热度与可预约性展示。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LabInfoServiceImpl implements LabInfoService {

    private final LabInfoMapper labInfoMapper;
    private final DeviceMapper deviceMapper;
    private final LabReservationMapper reservationMapper;

    @Override
    public IPage<LabInfoVO> page(IPage<LabInfoVO> page, String labType, String labName,
                                 Integer openStatus, String keyword) {
        LambdaQueryWrapper<LabInfo> wrapper = new LambdaQueryWrapper<LabInfo>()
                .eq(StrUtil.isNotBlank(labType), LabInfo::getLabType, labType)
                .like(StrUtil.isNotBlank(labName), LabInfo::getLabName, labName)
                .eq(openStatus != null, LabInfo::getOpenStatus, openStatus)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(LabInfo::getLabCode, keyword)
                        .or().like(LabInfo::getLabName, keyword)
                        .or().like(LabInfo::getLocation, keyword))
                .orderByDesc(LabInfo::getCreateTime);

        Page<LabInfo> entityPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<LabInfo> result = labInfoMapper.selectPage(entityPage, wrapper);
        Page<LabInfoVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    @Override
    public List<LabInfoVO> listAll() {
        return labInfoMapper.selectList(new LambdaQueryWrapper<LabInfo>()
                        .eq(LabInfo::getEnableReserve, 1)
                        .orderByAsc(LabInfo::getLabName))
                .stream().map(this::convert).toList();
    }

    @Override
    public LabInfoVO detail(Long id) {
        LabInfo lab = labInfoMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        return convert(lab);
    }

    @Override
    public void add(LabInfoDTO dto) {
        Long count = labInfoMapper.selectCount(new LambdaQueryWrapper<LabInfo>()
                .eq(LabInfo::getLabCode, dto.getLabCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "实验室编号已存在");
        }
        LabInfo lab = new LabInfo();
        BeanUtils.copyProperties(dto, lab);
        lab.setOpenStatus(0);               // 新增默认未开放，由管理员开关
        lab.setEnableReserve(1);
        labInfoMapper.insert(lab);
    }

    @Override
    public void update(LabInfoDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "实验室ID不能为空");
        }
        LabInfo lab = labInfoMapper.selectById(dto.getId());
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        Long count = labInfoMapper.selectCount(new LambdaQueryWrapper<LabInfo>()
                .eq(LabInfo::getLabCode, dto.getLabCode())
                .ne(LabInfo::getId, dto.getId()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "实验室编号已存在");
        }
        BeanUtils.copyProperties(dto, lab);
        OptimisticLockGuard.check(labInfoMapper.updateById(lab), "该实验室");
    }

    @Override
    public void delete(Long id) {
        LabInfo lab = labInfoMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        // 存在预约则阻止删除，保护业务数据完整性
        Long reserving = reservationMapper.selectCount(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getLabId, id));
        if (reserving != null && reserving > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该实验室存在预约记录，无法删除");
        }
        labInfoMapper.deleteById(id); // 逻辑删除
    }

    @Override
    public void toggleOpen(Long id, Integer openStatus) {
        LabInfo lab = labInfoMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        if (openStatus != 0 && openStatus != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开关状态不合法");
        }
        lab.setOpenStatus(openStatus);
        OptimisticLockGuard.check(labInfoMapper.updateById(lab), "该实验室");
    }

    @Override
    public void toggleReserve(Long id, Integer enableReserve) {
        LabInfo lab = labInfoMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        if (enableReserve != 0 && enableReserve != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约开关不合法");
        }
        lab.setEnableReserve(enableReserve);
        OptimisticLockGuard.check(labInfoMapper.updateById(lab), "该实验室");
    }

    /**
     * 实体 -> VO，顺带计算故障锁定与今日预约数等实时指标
     */
    private LabInfoVO convert(LabInfo lab) {
        LabInfoVO vo = new LabInfoVO();
        BeanUtils.copyProperties(lab, vo);
        // 故障锁定：所属实验室存在故障设备
        List<Device> faults = deviceMapper.selectFaultDevicesByLabId(lab.getId());
        vo.setDeviceFault(!faults.isEmpty());
        // 今日预约数
        Long today = reservationMapper.selectCount(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getLabId, lab.getId())
                .eq(LabReservation::getReserveDate, LocalDate.now())
                .notIn(LabReservation::getStatus,
                        ReservationStatus.CANCELLED.name(), ReservationStatus.REJECTED.name()));
        vo.setTodayReservationCount(today == null ? 0L : today);
        return vo;
    }
}