package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        voResult.setRecords(convertBatch(result.getRecords()));
        return voResult;
    }

    @Override
    public List<LabInfoVO> listAll() {
        return convertBatch(labInfoMapper.selectList(new LambdaQueryWrapper<LabInfo>()
                .eq(LabInfo::getEnableReserve, 1)
                .orderByAsc(LabInfo::getLabName)));
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
        if (lab.getShareMode() == null) {
            lab.setShareMode(1);            // 默认按人数共享
        }
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
        if (lab.getShareMode() == null) {
            lab.setShareMode(1);
        }
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

    @Override
    public void toggleShareMode(Long id, Integer shareMode) {
        LabInfo lab = labInfoMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        if (shareMode != 0 && shareMode != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "占用模式不合法");
        }
        lab.setShareMode(shareMode);
        OptimisticLockGuard.check(labInfoMapper.updateById(lab), "该实验室");
    }

    /**
     * 实体 -> VO，顺带计算故障锁定与今日预约数等实时指标
     */
    private LabInfoVO convert(LabInfo lab) {
        return convertBatch(List.of(lab)).get(0);
    }

    /**
     * 批量转换：故障设备与今日预约数各自只查一次，
     * 避免逐行调用 {@link #convert} 造成 2N 次数据库往返。
     */
    private List<LabInfoVO> convertBatch(List<LabInfo> labs) {
        if (labs == null || labs.isEmpty()) {
            return List.of();
        }
        List<Long> labIds = labs.stream().map(LabInfo::getId).toList();

        // 故障锁定：一次性取出这批实验室中「存在故障设备」的 labId
        Set<Long> faultLabIds = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .select(Device::getLabId)
                        .in(Device::getLabId, labIds)
                        .eq(Device::getFaultStatus, 1))
                .stream()
                .map(Device::getLabId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 今日预约数：按实验室分组一次聚合
        Map<Long, Long> todayCountByLab = new HashMap<>();
        QueryWrapper<LabReservation> countWrapper = new QueryWrapper<LabReservation>()
                .select("lab_id AS labId", "COUNT(*) AS cnt")
                .in("lab_id", labIds)
                .eq("reserve_date", LocalDate.now())
                .notIn("status", ReservationStatus.CANCELLED.name(),
                        ReservationStatus.REJECTED.name())
                .groupBy("lab_id");
        reservationMapper.selectMaps(countWrapper).forEach(m -> todayCountByLab.put(
                Long.valueOf(m.get("labId").toString()),
                ((Number) m.get("cnt")).longValue()));

        return labs.stream().map(lab -> {
            LabInfoVO vo = new LabInfoVO();
            BeanUtils.copyProperties(lab, vo);
            vo.setDeviceFault(faultLabIds.contains(lab.getId()));
            vo.setTodayReservationCount(todayCountByLab.getOrDefault(lab.getId(), 0L));
            return vo;
        }).toList();
    }
}