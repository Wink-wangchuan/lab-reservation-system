package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.OccupancyCalculator;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabClosure;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.entity.LabWaitlist;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.enums.WaitlistStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.mapper.LabWaitlistMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.LabClosureService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.service.WaitlistService;
import com.lab.reservation.vo.WaitlistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 候补队列服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：约满排队 + 自动递补</b></p>
 * 时段容量被占满后，用户可以进入候补队列；定时任务按「先来先服务」顺序，
 * 在容量释放后自动为排队用户生成待审核预约，避免空位在没人盯屏时被浪费。
 *
 * <p>递补前会重新校验实验室开关、闭馆日历、爽约限制与实时容量，
 * 并锁住实验室行串行化「先查后插」，不会因为并发提交而超卖。</p>
 *
 * @author LabWise
 * @since 3.7.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private final LabWaitlistMapper waitlistMapper;
    private final LabInfoMapper labInfoMapper;
    private final LabTimeSlotMapper slotMapper;
    private final LabReservationMapper reservationMapper;
    private final DeviceMapper deviceMapper;
    private final SysUserMapper sysUserMapper;
    private final LabClosureService closureService;
    private final NoticeService noticeService;
    private final StatisticsService statisticsService;

    /** 不占用容量的预约状态 */
    private static final List<String> BANNED_STATUS = List.of(
            ReservationStatus.DRAFT.name(),
            ReservationStatus.CANCELLED.name(),
            ReservationStatus.REJECTED.name());

    /** 单次定时任务最多处理的候补条数 */
    private static final int PROMOTE_BATCH = 100;

    /** 定时任务开关（默认开启） */
    @org.springframework.beans.factory.annotation.Value("${lab.schedule.promote-waitlist:true}")
    private boolean promoteEnabled = true;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WaitlistVO join(ReservationSubmitDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();
        LabInfo lab = labInfoMapper.selectById(dto.getLabId());
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        if (lab.getEnableReserve() == null || lab.getEnableReserve() != 1) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW, "该实验室已被禁止预约");
        }
        if (lab.getOpenStatus() == null || lab.getOpenStatus() != 1) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW, "该实验室预约未开放");
        }
        LocalDate date = dto.getReserveDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约日期不能早于今天");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        LabClosure closure = closureService.findClosure(lab.getId(), date);
        if (closure != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "该实验室当日闭馆"
                            + (StrUtil.isNotBlank(closure.getReason()) ? "（" + closure.getReason() + "）" : ""));
        }
        if (slotLimit(lab.getId(), date, dto.getStartTime(), dto.getEndTime()) == null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "预约时段不在该实验室的开放时间段内");
        }
        List<Device> faultDevices = deviceMapper.selectFaultDevicesByLabId(lab.getId());
        if (!faultDevices.isEmpty()) {
            throw new BusinessException(ResultCode.RESERVATION_LOCKED,
                    "实验室存在故障设备，已锁定，暂不可排队候补");
        }
        SysUser user = sysUserMapper.selectById(current.getUserId());
        if (user != null && user.getNoShowBanUntil() != null
                && user.getNoShowBanUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "因连续爽约，你的预约权限已被限制至 "
                            + user.getNoShowBanUntil().toLocalDate() + "，暂不能加入候补");
        }

        // 只有真的约满才允许排队，仍有余量时直接引导去预约，避免队伍被无意义占位
        List<LabReservation> overlapping = reservationMapper.selectConflictingReservations(
                lab.getId(), date, dto.getStartTime(), dto.getEndTime(), null, BANNED_STATUS);
        int peak = OccupancyCalculator.peakPeople(overlapping, dto.getStartTime(), dto.getEndTime());
        int limit = peopleLimit(lab, slotLimit(lab.getId(), date, dto.getStartTime(), dto.getEndTime()));
        if (limit <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该时段人数不限，仍有可约余量，请直接提交预约");
        }
        if (peak + dto.getPeopleCount() <= limit) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "该时段仍有余量（剩余可约 " + Math.max(0, limit - peak) + " 人），请直接提交预约");
        }

        Long duplicated = waitlistMapper.selectCount(new LambdaQueryWrapper<LabWaitlist>()
                .eq(LabWaitlist::getUserId, current.getUserId())
                .eq(LabWaitlist::getLabId, lab.getId())
                .eq(LabWaitlist::getReserveDate, date)
                .eq(LabWaitlist::getStatus, WaitlistStatus.WAITING.name())
                .lt(LabWaitlist::getStartTime, dto.getEndTime())
                .gt(LabWaitlist::getEndTime, dto.getStartTime()));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "你已在该时段的候补队列中，无需重复排队");
        }

        LabWaitlist row = new LabWaitlist();
        row.setLabId(lab.getId());
        row.setLabName(lab.getLabName());
        row.setReserveDate(date);
        row.setStartTime(dto.getStartTime());
        row.setEndTime(dto.getEndTime());
        row.setUserId(current.getUserId());
        row.setUserName(current.getRealName());
        row.setPeopleCount(dto.getPeopleCount());
        row.setPurpose(dto.getPurpose());
        row.setStatus(WaitlistStatus.WAITING.name());
        waitlistMapper.insert(row);
        noticeService.push(current.getUserId(), "已加入候补队列",
                String.format("实验室「%s」%s %s-%s 已约满，你已进入候补队列；有人取消或驳回释放容量后会按顺序自动递补。",
                        lab.getLabName(), date, dto.getStartTime(), dto.getEndTime()),
                NoticeType.RESERVATION, row.getId());
        return convert(row);
    }

    @Override
    public List<WaitlistVO> mine() {
        List<LabWaitlist> list = waitlistMapper.selectList(new LambdaQueryWrapper<LabWaitlist>()
                .eq(LabWaitlist::getUserId, SecurityUtil.getUserId())
                .orderByDesc(LabWaitlist::getCreateTime)
                .last("LIMIT 50"));
        return list.stream().map(this::convert).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        LabWaitlist row = waitlistMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "候补记录不存在");
        }
        if (!row.getUserId().equals(SecurityUtil.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消自己的候补");
        }
        if (WaitlistStatus.of(row.getStatus()) != WaitlistStatus.WAITING) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "当前状态为[" + WaitlistStatus.of(row.getStatus()).getDesc() + "]，无法取消");
        }
        row.setStatus(WaitlistStatus.CANCELLED.name());
        waitlistMapper.updateById(row);
        noticeService.push(row.getUserId(), "已退出候补队列",
                String.format("实验室「%s」%s %s-%s 的候补已取消。",
                        row.getLabName(), row.getReserveDate(), row.getStartTime(), row.getEndTime()),
                NoticeType.RESERVATION, row.getId());
    }

    @Override
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void promoteWaiting() {
        if (!promoteEnabled) {
            return;
        }
        List<LabWaitlist> list = waitlistMapper.selectList(new LambdaQueryWrapper<LabWaitlist>()
                .eq(LabWaitlist::getStatus, WaitlistStatus.WAITING.name())
                .orderByAsc(LabWaitlist::getCreateTime)
                .last("LIMIT " + PROMOTE_BATCH));
        if (list.isEmpty()) {
            return;
        }
        int promoted = 0;
        for (LabWaitlist row : list) {
            try {
                if (promoteOne(row)) {
                    promoted++;
                }
            } catch (Exception e) {
                // 单条失败不影响队列里的其他人，下一轮继续尝试
                log.warn("[候补] 递补失败 waitlistId={}", row.getId(), e);
            }
        }
        if (promoted > 0) {
            statisticsService.evictStatCache();
            log.info("[候补] 自动递补完成，本次递补 {} 条", promoted);
        }
    }

    /* ==================== 内部实现 ==================== */

    /**
     * 尝试递补一条：容量够就生成待审核预约，永远排不上则继续留在队列里。
     *
     * @return 是否成功递补
     */
    private boolean promoteOne(LabWaitlist row) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        if (row.getReserveDate().isBefore(today)
                || (row.getReserveDate().equals(today) && row.getEndTime().isBefore(now.toLocalTime()))) {
            expire(row, "预约时段已过");
            return false;
        }
        LabInfo lab = labInfoMapper.selectById(row.getLabId());
        if (lab == null || lab.getOpenStatus() == null || lab.getOpenStatus() != 1
                || lab.getEnableReserve() == null || lab.getEnableReserve() != 1) {
            expire(row, "实验室已关闭预约");
            return false;
        }
        LabClosure closure = closureService.findClosure(lab.getId(), row.getReserveDate());
        if (closure != null) {
            expire(row, "实验室闭馆");
            return false;
        }
        SysUser user = sysUserMapper.selectById(row.getUserId());
        if (user == null || (user.getNoShowBanUntil() != null && user.getNoShowBanUntil().isAfter(now))) {
            expire(row, "账号处于爽约限制期");
            return false;
        }

        // 锁住实验室行：与用户提交预约使用同一把锁，保证「查容量 + 建单」不被并发穿透
        reservationMapper.lockLabRow(lab.getId());
        List<LabReservation> overlapping = reservationMapper.selectConflictingReservations(
                lab.getId(), row.getReserveDate(), row.getStartTime(), row.getEndTime(), null, BANNED_STATUS);
        int peak = OccupancyCalculator.peakPeople(overlapping, row.getStartTime(), row.getEndTime());
        int limit = peopleLimit(lab, slotLimit(lab.getId(), row.getReserveDate(),
                row.getStartTime(), row.getEndTime()));
        if (limit > 0 && peak + row.getPeopleCount() > limit) {
            return false;   // 还没轮得到，继续排队
        }

        LabReservation reservation = new LabReservation();
        reservation.setReservationNo(generateNo());
        reservation.setLabId(lab.getId());
        reservation.setLabName(lab.getLabName());
        reservation.setStudentId(row.getUserId());
        reservation.setStudentName(row.getUserName());
        reservation.setReserveDate(row.getReserveDate());
        reservation.setStartTime(row.getStartTime());
        reservation.setEndTime(row.getEndTime());
        reservation.setPurpose(row.getPurpose());
        reservation.setPeopleCount(row.getPeopleCount());
        reservation.setUseDeviceIds("");
        reservation.setStatus(ReservationStatus.PENDING.name());
        reservationMapper.insert(reservation);

        row.setStatus(WaitlistStatus.PROMOTED.name());
        waitlistMapper.updateById(row);
        statisticsService.recordReservationBooking(lab.getId(), lab.getLabName());
        noticeService.push(row.getUserId(), "候补成功，已为你提交预约",
                String.format("实验室「%s」%s %s-%s 的候补已递补成功，系统已自动提交预约（等待审核）。",
                        lab.getLabName(), row.getReserveDate(), row.getStartTime(), row.getEndTime()),
                NoticeType.RESERVATION, reservation.getId());
        return true;
    }

    /** 候补失效：记录状态并通知本人 */
    private void expire(LabWaitlist row, String reason) {
        row.setStatus(WaitlistStatus.EXPIRED.name());
        waitlistMapper.updateById(row);
        noticeService.push(row.getUserId(), "候补已失效",
                String.format("实验室「%s」%s %s-%s 的候补因「%s」已失效。",
                        row.getLabName(), row.getReserveDate(), row.getStartTime(),
                        row.getEndTime(), reason),
                NoticeType.RESERVATION, null);
    }

    /**
     * 所命中时段的人数上限（0 表示不限）；不等返回 null 表示该时段不在任何开放时段内
     */
    private Integer slotLimit(Long labId, LocalDate date, LocalTime start, LocalTime end) {
        int weekDay = date.getDayOfWeek().getValue();
        List<LabTimeSlot> matched = slotMapper.selectList(new LambdaQueryWrapper<LabTimeSlot>()
                        .eq(LabTimeSlot::getLabId, labId)
                        .eq(LabTimeSlot::getWeekDay, weekDay)
                        .eq(LabTimeSlot::getStatus, 1)).stream()
                .filter(s -> !s.getStartTime().isAfter(start) && !s.getEndTime().isBefore(end))
                .toList();
        if (matched.isEmpty()) {
            return null;
        }
        return matched.stream()
                .map(LabTimeSlot::getMaxPeople)
                .filter(v -> v != null && v > 0)
                .min(Integer::compareTo)
                .orElse(0);
    }

    /**
     * 该时段的有效人数上限：实验室容量与时段上限取更严格的一个，0 表示不限
     */
    private int peopleLimit(LabInfo lab, Integer slotMax) {
        int capacity = lab.getCapacity() == null ? 0 : lab.getCapacity();
        int slot = slotMax == null ? 0 : slotMax;
        if (capacity > 0 && slot > 0) {
            return Math.min(capacity, slot);
        }
        return capacity > 0 ? capacity : slot;
    }

    private WaitlistVO convert(LabWaitlist row) {
        WaitlistVO vo = new WaitlistVO();
        BeanUtils.copyProperties(row, vo);
        vo.setStatusDesc(WaitlistStatus.of(row.getStatus()).getDesc());
        return vo;
    }

    /** 生成预约单号：与预约服务保持同一格式（RSV + 时间戳 + 4 位随机） */
    private String generateNo() {
        LocalDateTime now = LocalDateTime.now();
        String ts = String.format("%04d%02d%02d%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
        return "RSV" + ts + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase();
    }
}
