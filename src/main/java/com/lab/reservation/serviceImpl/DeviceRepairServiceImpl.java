package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.DeviceRepairDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.DeviceRepair;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.RepairStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.DeviceRepairMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.DeviceRepairService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.RepairDeviceOptionVO;
import com.lab.reservation.vo.RepairVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备报修闭环服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：设备故障锁定与解锁闭环</b></p>
 * <ul>
 *   <li>报修状态管理由 {@link RepairStatus} 状态机约束</li>
 *   <li>提交报修 → device.fault_status=1（该设备所属实验室预约被锁定）</li>
 *   <li>维修完成/取消 → 若设备已无其他进行中报修，则 fault_status=0，实验室解锁</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DeviceRepairServiceImpl implements DeviceRepairService {

    private final DeviceRepairMapper repairMapper;
    private final DeviceMapper deviceMapper;
    /** 报修流转影响待处理/故障设备等统计，变更后需失效统计缓存 */
    private final StatisticsService statisticsService;
    /** 报修流转节点向报修人推送站内消息 */
    private final NoticeService noticeService;
    /** 按权限定向推送：把新工单推给具备维修处理权限的人 */
    private final SysRoleMapper roleMapper;
    /** 设备故障会锁定所属实验室，需要通知实验室负责人 */
    private final LabInfoMapper labInfoMapper;
    /** 校验实验室负责人是否仍是可用账号，避免把消息推给已删除/停用账号 */
    private final SysUserMapper userMapper;

    /** 维修处理权限码 */
    private static final String PERM_HANDLE = "device:repair:handle";

    /** 进行中报修状态（仍占用/锁定） */
    private static final List<String> ACTIVE_STATUS =
            List.of(RepairStatus.PENDING.name(), RepairStatus.REPAIRING.name());

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairVO submit(DeviceRepairDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();
        Device device = deviceMapper.selectById(dto.getDeviceId());
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (device.getStatus() != null && device.getStatus() == 2) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "报废设备无需报修");
        }
        // 并发串行化：锁住设备行，避免同一设备并发提交绕过「进行中报修」校验而重复建单
        repairMapper.lockDeviceRow(dto.getDeviceId());

        // 已存在进行中的报修则不允许重复提交
        Long active = repairMapper.selectCount(new LambdaQueryWrapper<DeviceRepair>()
                .eq(DeviceRepair::getDeviceId, dto.getDeviceId())
                .in(DeviceRepair::getStatus, ACTIVE_STATUS));
        if (active != null && active > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该设备存在进行中的报修单");
        }

        // 1. 建报修单
        DeviceRepair repair = new DeviceRepair();
        repair.setRepairNo(generateNo());
        repair.setDeviceId(device.getId());
        repair.setDeviceName(device.getDeviceName());
        repair.setLabId(device.getLabId());
        repair.setReporterId(current.getUserId());
        repair.setReporterName(current.getRealName());
        repair.setFaultDesc(dto.getFaultDesc());
        repair.setStatus(RepairStatus.PENDING.name());
        repairMapper.insert(repair);

        // 2. 锁定设备（故障），进而锁定所属实验室预约
        lockDevice(device, dto.getFaultDesc());
        statisticsService.evictStatCache();
        noticeService.push(current.getUserId(), "报修已提交",
                String.format("设备「%s」的报修单 %s 已提交，"
                                + "该设备所属实验室已临时锁定，请等待处理。",
                        device.getDeviceName(), repair.getRepairNo()),
                NoticeType.REPAIR, repair.getId());
        // 通知具备维修处理权限的人员：让新工单直接进入其待办
        List<Long> handlerIds = new java.util.ArrayList<>(
                roleMapper.selectUserIdsByPermission(PERM_HANDLE));
        handlerIds.removeIf(id -> id.equals(current.getUserId()));
        noticeService.pushAll(handlerIds, "新的设备报修待处理",
                String.format("%s 提交了设备「%s」的报修：%s",
                        current.getRealName(), device.getDeviceName(),
                        StrUtil.maxLength(dto.getFaultDesc(), 100)),
                NoticeType.REPAIR, repair.getId());
        // 实验室被锁定：告知实验室负责人预约已暂停
        notifyLabManager(device.getLabId(), "实验室设备故障已锁定",
                String.format("设备「%s」报修，实验室「%s」已临时锁定并暂停预约。",
                        device.getDeviceName(), device.getLabName()),
                repair.getId());

        return convert(repair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairVO start(Long repairId, String remark) {
        LoginUser current = SecurityUtil.getLoginUser();
        DeviceRepair repair = getRepair(repairId);
        transition(repair, RepairStatus.REPAIRING);
        repair.setHandlerId(current.getUserId());
        repair.setHandlerName(current.getRealName());
        repair.setHandleRemark(remark);
        repair.setStartTime(LocalDateTime.now());
        repairMapper.updateById(repair);
        statisticsService.evictStatCache();
        noticeService.push(repair.getReporterId(), "报修已受理，维修进行中",
                String.format("设备「%s」的报修单 %s 已由 %s 接单处理。",
                        repair.getDeviceName(), repair.getRepairNo(), current.getRealName()),
                NoticeType.REPAIR, repair.getId());
        return convert(repair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairVO complete(Long repairId, String remark) {
        DeviceRepair repair = getRepair(repairId);
        transition(repair, RepairStatus.COMPLETED);
        repair.setHandleRemark(remark);
        repair.setFinishTime(LocalDateTime.now());
        repairMapper.updateById(repair);
        // 若设备无其他进行中报修 → 解锁设备与实验室
        boolean unlocked = unlockDeviceIfNoActive(repair.getDeviceId());
        statisticsService.evictStatCache();
        noticeService.push(repair.getReporterId(), "报修已完成",
                String.format("设备「%s」的报修单 %s 已维修完成，设备已解锁。",
                        repair.getDeviceName(), repair.getRepairNo()),
                NoticeType.REPAIR, repair.getId());
        // 实验室恢复可预约时告知负责人
        if (unlocked) {
            notifyLabManager(repair.getLabId(), "实验室已恢复可预约",
                    String.format("设备「%s」维修完成，实验室已解除锁定，恢复预约。",
                            repair.getDeviceName()),
                    repair.getId());
        }
        return convert(repair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairVO cancel(Long repairId, String remark) {
        LoginUser current = SecurityUtil.getLoginUser();
        DeviceRepair repair = getRepair(repairId);
        // 取消语义为「报修人撤回」，因此仅报修人本人可取消，避免他人越权关闭工单
        if (!repair.getReporterId().equals(current.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消本人提交的报修单");
        }
        transition(repair, RepairStatus.CANCELLED);
        repair.setHandleRemark(remark);
        repairMapper.updateById(repair);
        boolean unlocked = unlockDeviceIfNoActive(repair.getDeviceId());
        statisticsService.evictStatCache();
        noticeService.push(repair.getReporterId(), "报修已取消",
                String.format("设备「%s」的报修单 %s 已取消，设备已解锁。",
                        repair.getDeviceName(), repair.getRepairNo()),
                NoticeType.REPAIR, repair.getId());
        if (unlocked) {
            notifyLabManager(repair.getLabId(), "实验室已恢复可预约",
                    String.format("设备「%s」的报修已取消，实验室已解除锁定，恢复预约。",
                            repair.getDeviceName()),
                    repair.getId());
        }
        return convert(repair);
    }

    @Override
    public RepairVO detail(Long repairId) {
        DeviceRepair repair = getRepair(repairId);
        // 仅报修人本人、或具备报修管理/处理权限的角色可查看，防止越权读取他人报修信息
        LoginUser current = SecurityUtil.getLoginUser();
        boolean owner = repair.getReporterId().equals(current.getUserId());
        boolean manager = SecurityUtil.hasPermission("device:repair:list")
                || SecurityUtil.hasPermission("device:repair:handle");
        if (!owner && !manager) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该报修单");
        }
        return convert(repair);
    }

    @Override
    public IPage<RepairVO> page(IPage<RepairVO> page, Long labId, String status, String keyword) {
        LambdaQueryWrapper<DeviceRepair> wrapper = new LambdaQueryWrapper<DeviceRepair>()
                .eq(labId != null, DeviceRepair::getLabId, labId)
                .eq(StrUtil.isNotBlank(status), DeviceRepair::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(DeviceRepair::getRepairNo, keyword)
                        .or().like(DeviceRepair::getDeviceName, keyword)
                        .or().like(DeviceRepair::getReporterName, keyword))
                .orderByDesc(DeviceRepair::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public IPage<RepairVO> pageMine(IPage<RepairVO> page, String status) {
        Long userId = SecurityUtil.getUserId();
        LambdaQueryWrapper<DeviceRepair> wrapper = new LambdaQueryWrapper<DeviceRepair>()
                .eq(DeviceRepair::getReporterId, userId)
                .eq(StrUtil.isNotBlank(status), DeviceRepair::getStatus, status)
                .orderByDesc(DeviceRepair::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public List<RepairDeviceOptionVO> deviceOptions() {
        // 报废设备无需报修，其余全部列出（故障中的设备由前端置灰，避免重复报修）
        // 只回传 id/编号/名称/所属实验室/故障状态，不带出价格等资产字段
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .ne(Device::getStatus, 2)
                        .orderByAsc(Device::getLabName)
                        .orderByAsc(Device::getDeviceName))
                .stream().map(device -> {
                    RepairDeviceOptionVO vo = new RepairDeviceOptionVO();
                    vo.setId(device.getId());
                    vo.setDeviceCode(device.getDeviceCode());
                    vo.setDeviceName(device.getDeviceName());
                    vo.setLabId(device.getLabId());
                    vo.setLabName(device.getLabName());
                    vo.setFaultStatus(device.getFaultStatus());
                    return vo;
                }).toList();
    }

    /* ==================== 辅助逻辑 ==================== */

    private DeviceRepair getRepair(Long id) {
        // 并发串行化：锁住报修单行，使状态机「校验 + 更新」串行执行
        repairMapper.lockRepairRow(id);
        DeviceRepair repair = repairMapper.selectById(id);
        if (repair == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报修单不存在");
        }
        return repair;
    }

    /**
     * 状态机流转校验
     */
    private void transition(DeviceRepair repair, RepairStatus target) {
        RepairStatus from = RepairStatus.of(repair.getStatus());
        if (!RepairStatus.canTransition(from, target)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "报修状态不允许从[" + from.getDesc() + "]流转到[" + target.getDesc() + "]");
        }
        repair.setStatus(target.name());
    }

    /**
     * 锁定设备：标记故障（进而锁定实验室预约）
     */
    private void lockDevice(Device device, String faultDesc) {
        Device update = new Device();
        update.setId(device.getId());
        update.setFaultStatus(1);
        update.setFaultDesc(faultDesc);
        deviceMapper.updateById(update);
    }

    /**
     * 解锁设备：无其他进行中报修才允许解除，保持故障状态一致性
     *
     * @return 是否真的执行了解锁（用于决定是否通知实验室负责人）
     */
    private boolean unlockDeviceIfNoActive(Long deviceId) {
        Long active = repairMapper.selectCount(new LambdaQueryWrapper<DeviceRepair>()
                .eq(DeviceRepair::getDeviceId, deviceId)
                .in(DeviceRepair::getStatus, ACTIVE_STATUS));
        if (active == null || active == 0) {
            Device update = new Device();
            update.setId(deviceId);
            update.setFaultStatus(0);
            update.setFaultDesc(null);
            deviceMapper.updateById(update);
            return true;
        }
        return false;
    }

    /**
     * 通知实验室负责人（manager_id）：故障锁定/恢复可预约等影响其管辖实验室的事件
     */
    private void notifyLabManager(Long labId, String title, String content, Long bizId) {
        if (labId == null) {
            return;
        }
        LabInfo lab = labInfoMapper.selectById(labId);
        if (lab == null || lab.getManagerId() == null) {
            return;
        }
        // 负责人本人操作触发的事件不重复推送给自己
        if (lab.getManagerId().equals(SecurityUtil.getUserId())) {
            return;
        }
        // 负责人可能已被删除或停用（selectById 已过滤逻辑删除）
        var manager = userMapper.selectById(lab.getManagerId());
        if (manager == null || manager.getStatus() == null || manager.getStatus() != 1) {
            return;
        }
        noticeService.push(lab.getManagerId(), title, content, NoticeType.REPAIR, bizId);
    }

    private String generateNo() {
        LocalDateTime now = LocalDateTime.now();
        String ts = String.format("%04d%02d%02d%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
        return "RPR" + ts + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase();
    }

    private IPage<RepairVO> queryPage(IPage<RepairVO> page,
                                      LambdaQueryWrapper<DeviceRepair> wrapper) {
        Page<DeviceRepair> entityPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<DeviceRepair> result = repairMapper.selectPage(entityPage, wrapper);
        Page<RepairVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    private RepairVO convert(DeviceRepair repair) {
        RepairVO vo = new RepairVO();
        BeanUtils.copyProperties(repair, vo);
        RepairStatus status = RepairStatus.of(repair.getStatus());
        vo.setStatusDesc(status == null ? "未知" : status.getDesc());
        return vo;
    }
}