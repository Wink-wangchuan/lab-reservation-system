package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.OccupancyCalculator;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.RescheduleDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabApproval;
import com.lab.reservation.entity.LabCheckin;
import com.lab.reservation.entity.LabClosure;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.CheckinStatus;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.event.LabSeatReleasedEvent;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabApprovalMapper;
import com.lab.reservation.mapper.LabCheckinMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.CheckinService;
import com.lab.reservation.service.LabClosureService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.ReservationService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预约业务服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：时段按人数共享 + 并发峰值校验</b></p>
 * 两个时间区间 [a,b) 与 [c,d) 相交的条件是：
 * <pre>a &lt; d AND b &gt; c</pre>
 * 相交不代表冲突——同一时段允许多单共享一间实验室，只要累计人数不超容量。
 * 已占用人数由 {@link OccupancyCalculator} 用扫描线求出区间内的并发峰值，
 * 再与「实验室容量」「所命中时段的人数上限」比较；
 * 配合预约状态过滤（草稿/取消/驳回不占用）、设备故障锁定、开放时段校验构成完整防线。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final LabInfoMapper labInfoMapper;
    private final LabTimeSlotMapper labTimeSlotMapper;
    private final LabReservationMapper reservationMapper;
    private final DeviceMapper deviceMapper;
    private final LabApprovalMapper approvalMapper;
    private final StatisticsService statisticsService;
    /** 审批通过后生成签到凭证，构成预约闭环 */
    private final CheckinService checkinService;
    /** 预约流转节点主动推送站内消息 */
    private final NoticeService noticeService;
    /** 判定提交人是否教师：教师提交的预约需由管理员审批 */
    private final SysRoleMapper roleMapper;
    /** 爽约限制：连续爽约满额后禁止预约一段时间 */
    private final SysUserMapper sysUserMapper;
    /** 开始前提醒需要跳过已签到的预约 */
    private final LabCheckinMapper checkinMapper;
    /** 闭馆日历：节假日/临时维护期间不可预约 */
    private final LabClosureService closureService;
    /** 容量释放事件：取消/驳回/改期后立即触发候补递补（事件监听器异步处理） */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** 系统设置：定时任务开关可在管理页实时启停 */
    private final com.lab.reservation.service.SysConfigService sysConfigService;

    /** 数据权限：受限用户只能看到授权实验室的预约 */
    private final com.lab.reservation.service.DataScopeService dataScopeService;

    /** 批量审批按单开启独立事务：某一张失败不牵连已成功的单据 */
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** 预约开始前多少分钟提醒 */
    private static final int REMIND_MINUTES = 30;

    /**
     * 定时任务开关（默认开启，可在配置中按需关闭）
     *
     * <p>用字段初始化值而非仅靠 @Value，保证纯单元测试环境（不加载容器）里
     * 直接调用定时方法时仍是「开启」语义，不会因为注入缺失而静默跳过。</p>
     */
    @org.springframework.beans.factory.annotation.Value("${lab.schedule.auto-complete-reservation:true}")
    private boolean autoCompleteEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${lab.schedule.remind-reservation:true}")
    private boolean remindEnabled = true;

    /** 可审批教师提交预约的角色（教师本人不能审批教师单） */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_LAB_ADMIN = "LAB_ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    /** 预约审批权限码：用于查找「该由谁收到待审批提醒」 */
    private static final String PERM_APPROVE = "reservation:approve";

    /** 不参与"占用"判定的预约状态（草稿/已取消/已驳回 不阻塞后续预约） */
    private static final List<String> BANNED_STATUS =
            List.of(ReservationStatus.DRAFT.name(),
                    ReservationStatus.CANCELLED.name(),
                    ReservationStatus.REJECTED.name());

    /** 单次 Excel 导出的最大行数：防止一次性把整表读进内存 */
    private static final int MAX_EXPORT_ROWS = 20_000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO submit(ReservationSubmitDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();

        // ========== 0. 爽约限制：连续爽约满 3 次后一段时间内禁止预约 ==========
        assertNotRestricted(current.getUserId());

        // ========== 1. 实验室合法性 & 开放状态校验 ==========
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

        // ========== 1.5 并发串行化：锁住实验室行 ==========
        // 冲突校验是「先查后插」，不加锁时同一实验室同一时段的并发提交会同时通过校验并各自落库。
        reservationMapper.lockLabRow(lab.getId());

        // ========== 2. 时间合法性校验 ==========
        LocalDate date = dto.getReserveDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约日期不能早于今天");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        // 2.1 开放时段校验：请求时段必须被该实验室当日某个启用时段完整包含
        List<LabTimeSlot> matchedSlots = validateTimeWindow(lab.getId(), date,
                dto.getStartTime(), dto.getEndTime());
        // 2.1.1 闭馆日历：节假日 / 临时维护期间整间不可预约
        LabClosure closure = closureService.findClosure(lab.getId(), date);
        if (closure != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "该实验室当日闭馆"
                            + (StrUtil.isNotBlank(closure.getReason()) ? "（" + closure.getReason() + "）" : ""));
        }
        // 2.2 单次人数合法性：实验室容量是所有时段的公共上限，时段自身还可配置更小的人数上限
        int labCapacity = lab.getCapacity() == null ? 0 : lab.getCapacity();
        if (dto.getPeopleCount() > labCapacity) {
            throw new BusinessException(ResultCode.RESERVATION_OVER_CAPACITY,
                    "预约人数超过实验室容量(" + labCapacity + ")");
        }
        int slotLimit = slotPeopleLimit(matchedSlots);
        validateSlotCapacity(slotLimit, dto.getPeopleCount());

        // ========== 3. 设备故障锁定实验室校验 ==========
        validateDeviceLock(lab.getId(), dto.getUseDeviceIds());

        // ========== 4. 时段容量校验（答辩重点：按人数共享，不再独占整间实验室） ==========
        List<LabReservation> overlapping = reservationMapper.selectConflictingReservations(
                lab.getId(), date, dto.getStartTime(), dto.getEndTime(), null, BANNED_STATUS);

        // 4.1 同一用户在同一实验室同一时段只允许一条有效预约，避免自己把座位重复占掉
        LabReservation mine = overlapping.stream()
                .filter(r -> r.getStudentId() != null && r.getStudentId().equals(current.getUserId()))
                .findFirst()
                .orElse(null);
        if (mine != null) {
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                    "你在该时段已有预约（单号 " + mine.getReservationNo() + "），请先取消后再预约");
        }

        // 4.2 整间独占的实验室不参与共享：时段相交即视为冲突
        if (lab.isExclusive() && !overlapping.isEmpty()) {
            LabReservation first = overlapping.get(0);
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                    "该实验室为整间独占模式，该时段已被占用（单号 " + first.getReservationNo()
                            + "，状态 " + ReservationStatus.of(first.getStatus()).getDesc() + "）");
        }

        // 4.3 设备占用冲突：同一台设备在同一时段只能被一个预约使用
        assertDevicesFree(overlapping, dto.getUseDeviceIds());

        // 4.4 已占用人数取「区间内的并发峰值」：互相错开的两单不应被简单相加
        int booked = OccupancyCalculator.peakPeople(overlapping, dto.getStartTime(), dto.getEndTime());
        assertRemaining("实验室容量", labCapacity, booked, dto.getPeopleCount());
        if (slotLimit > 0) {
            assertRemaining("该时段人数上限", slotLimit, booked, dto.getPeopleCount());
        }

        // ========== 5. 组装并保存 ==========
        LabReservation reservation = new LabReservation();
        reservation.setReservationNo(generateNo());
        reservation.setLabId(lab.getId());
        reservation.setLabName(lab.getLabName());
        reservation.setStudentId(current.getUserId());
        reservation.setStudentName(current.getRealName());
        reservation.setReserveDate(date);
        reservation.setStartTime(dto.getStartTime());
        reservation.setEndTime(dto.getEndTime());
        reservation.setPurpose(dto.getPurpose());
        reservation.setPeopleCount(dto.getPeopleCount());
        reservation.setUseDeviceIds(StrUtil.join(",",
                dto.getUseDeviceIds() == null ? List.of() : dto.getUseDeviceIds()));
        reservation.setStatus(ReservationStatus.PENDING.name());   // 提交即进入待审核（教师单由管理员审批）
        reservationMapper.insert(reservation);
        // 累加热门实验室热度（Redis ZSet）
        statisticsService.recordReservationBooking(lab.getId(), lab.getLabName());
        // 推送站内消息：告知提交人已进入审核流程
        noticeService.push(current.getUserId(), "预约已提交，等待审核",
                String.format("实验室「%s」%s %s-%s 的预约已提交，请留意审核结果。",
                        lab.getLabName(), date, dto.getStartTime(), dto.getEndTime()),
                NoticeType.RESERVATION, reservation.getId());
        // 同时让有审批职责的人收到待办提醒（学生单 → 教师/实验室管理员；教师单 → 管理员）
        notifyApprovers(reservation, "新的实验室预约待审核",
                String.format("%s 提交了实验室「%s」%s %s-%s 的预约，请及时审批。",
                        current.getRealName(), lab.getLabName(), date,
                        dto.getStartTime(), dto.getEndTime()));
        return convert(reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reservationId, String reason) {
        LoginUser current = SecurityUtil.getLoginUser();
        // 并发串行化：先锁单行，避免并发取消绕过状态机校验
        reservationMapper.lockReservationRow(reservationId);
        LabReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约单不存在");
        }
        // 本人且处于可取消状态
        if (!reservation.getStudentId().equals(current.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消本人的预约");
        }
        ReservationStatus from = ReservationStatus.of(reservation.getStatus());
        if (!ReservationStatus.canTransition(from, ReservationStatus.CANCELLED)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "当前状态[" + from.getDesc() + "]不允许取消");
        }
        // 时间校验：已通过的预约一旦开始（含已结束）就不允许再取消，
        // 否则过期预约仍能被"取消"，等于篡改历史记录与统计数据
        if (from == ReservationStatus.APPROVED
                && !LocalDateTime.now().isBefore(
                        LocalDateTime.of(reservation.getReserveDate(), reservation.getStartTime()))) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "预约已开始或已结束，无法取消");
        }
        boolean wasPending = from == ReservationStatus.PENDING;
        reservation.setStatus(ReservationStatus.CANCELLED.name());
        reservation.setCancelReason(reason);
        reservationMapper.updateById(reservation);
        // 联动取消对应签到凭证，闭合状态一致性
        checkinService.cancelByReservation(reservation.getId(), reason);
        // 状态变更后让统计缓存失效，保证驾驶舱数据及时刷新
        statisticsService.evictStatCache();
        // 取消释放了该时段的容量：通知候补队列立即尝试递补，不必等下一个 5 分钟扫描
        publishSeatReleased(reservation.getLabId(), reservation.getReserveDate(), "预约取消");
        noticeService.push(reservation.getStudentId(), "预约已取消",
                String.format("实验室「%s」%s 的预约已取消。", reservation.getLabName(),
                        reservation.getReserveDate()),
                NoticeType.RESERVATION, reservation.getId());
        // 待审核单被撤回时，同步告知审批人，避免其继续在待办里处理已撤销的单
        if (wasPending) {
            notifyApprovers(reservation, "待审核预约已被撤回",
                    String.format("%s 撤回了实验室「%s」%s %s-%s 的预约申请，无需再审批。",
                            current.getRealName(), reservation.getLabName(),
                            reservation.getReserveDate(), reservation.getStartTime(),
                            reservation.getEndTime()));
        }
    }

    /**
     * 预约改期（本人）：只调整日期与时段，重新走一遍完整校验
     *
     * <p>改期本质上是「换一个时段占用容量」，因此不能直接改字段了事：
     * 开放时段、闭馆日历、设备故障、整间独占、同人重复、容量与设备占用
     * 全部要按新时段重算，并把旧时段占用的容量释放掉（更新即释放）。
     * 若原预约已通过，签到凭证的时段也要同步，否则核销窗口会落在旧时间上。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO reschedule(Long reservationId, RescheduleDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();
        // 先锁预约单行：避免与取消/审批并发，导致改期与状态流转互相覆盖
        reservationMapper.lockReservationRow(reservationId);
        LabReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约单不存在");
        }
        if (!reservation.getStudentId().equals(current.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能改期本人的预约");
        }
        ReservationStatus from = ReservationStatus.of(reservation.getStatus());
        if (from != ReservationStatus.PENDING && from != ReservationStatus.APPROVED) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "仅待审核或已通过的预约可改期，当前状态["
                            + (from == null ? "未知" : from.getDesc()) + "]");
        }
        LocalDateTime originStart =
                LocalDateTime.of(reservation.getReserveDate(), reservation.getStartTime());
        if (!LocalDateTime.now().isBefore(originStart)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "预约已开始或已结束，无法改期");
        }
        LabInfo lab = labInfoMapper.selectById(reservation.getLabId());
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        if (lab.getEnableReserve() == null || lab.getEnableReserve() != 1
                || lab.getOpenStatus() == null || lab.getOpenStatus() != 1) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW, "该实验室当前不可预约");
        }

        LocalDate date = dto.getReserveDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新的预约日期不能早于今天");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        List<LabTimeSlot> matchedSlots = validateTimeWindow(lab.getId(), date,
                dto.getStartTime(), dto.getEndTime());
        LabClosure closure = closureService.findClosure(lab.getId(), date);
        if (closure != null) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "该实验室当日闭馆"
                            + (StrUtil.isNotBlank(closure.getReason()) ? "（" + closure.getReason() + "）" : ""));
        }
        int peopleCount = reservation.getPeopleCount() == null ? 1 : reservation.getPeopleCount();
        int labCapacity = lab.getCapacity() == null ? 0 : lab.getCapacity();
        if (peopleCount > labCapacity) {
            throw new BusinessException(ResultCode.RESERVATION_OVER_CAPACITY,
                    "预约人数超过实验室容量(" + labCapacity + ")，请先取消后重新预约");
        }
        int slotLimit = slotPeopleLimit(matchedSlots);
        validateSlotCapacity(slotLimit, peopleCount);
        List<Long> deviceIds = parseDeviceIds(reservation.getUseDeviceIds());
        validateDeviceLock(lab.getId(), deviceIds);

        // 锁实验室行：与提交预约共用同一把锁，保证「查容量 + 更新」不被并发穿透
        reservationMapper.lockLabRow(lab.getId());
        List<LabReservation> overlapping = reservationMapper.selectConflictingReservations(
                lab.getId(), date, dto.getStartTime(), dto.getEndTime(),
                reservation.getId(), BANNED_STATUS);
        LabReservation mine = overlapping.stream()
                .filter(r -> r.getStudentId() != null && r.getStudentId().equals(current.getUserId()))
                .findFirst()
                .orElse(null);
        if (mine != null) {
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                    "你在该时段已有预约（单号 " + mine.getReservationNo() + "），请先取消后再改期");
        }
        if (lab.isExclusive() && !overlapping.isEmpty()) {
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                    "该实验室为整间独占模式，新时段已被占用（单号 "
                            + overlapping.get(0).getReservationNo() + "）");
        }
        assertDevicesFree(overlapping, deviceIds);
        int booked = OccupancyCalculator.peakPeople(overlapping, dto.getStartTime(), dto.getEndTime());
        assertRemaining("实验室容量", labCapacity, booked, peopleCount);
        if (slotLimit > 0) {
            assertRemaining("该时段人数上限", slotLimit, booked, peopleCount);
        }

        LocalDate oldDate = reservation.getReserveDate();
        java.time.LocalTime oldStart = reservation.getStartTime();
        reservation.setReserveDate(date);
        reservation.setStartTime(dto.getStartTime());
        reservation.setEndTime(dto.getEndTime());
        // 时段变了，原提醒标记必须清空，否则新的开始时间不会再提醒
        reservation.setReminderSent(0);
        reservationMapper.updateById(reservation);
        if (from == ReservationStatus.APPROVED) {
            // 已通过的单已有签到凭证，同步时段才能在新的窗口核销
            checkinService.rescheduleTimes(reservation);
        }
        statisticsService.evictStatCache();
        // 旧时段被释放：让原日期的候补也能立即递补
        publishSeatReleased(reservation.getLabId(), oldDate, "预约改期释放旧时段");
        noticeService.push(reservation.getStudentId(), "预约已改期",
                String.format("实验室「%s」的预约已从 %s %s 调整为 %s %s-%s。",
                        reservation.getLabName(), oldDate, oldStart, date,
                        dto.getStartTime(), dto.getEndTime()),
                NoticeType.RESERVATION, reservation.getId());
        notifyApprovers(reservation, "预约已改期",
                String.format("%s 将实验室「%s」的预约改到 %s %s-%s，请留意后续安排。",
                        current.getRealName(), reservation.getLabName(), date,
                        dto.getStartTime(), dto.getEndTime()));
        log.info("[预约] 改期成功 id={}, {} {} -> {} {}", reservation.getId(), oldDate, oldStart,
                date, dto.getStartTime());
        return convert(reservation);
    }

    /**
     * 定时推进已过期的预约（每 5 分钟）：
     *
     * <p>已通过（APPROVED）的预约在结束时刻之后自动流转为已完成（COMPLETED），
     * 与状态机中「APPROVED -> COMPLETED（到时自动完成）」的定义保持一致。</p>
     *
     * <p>这样处理后，过期预约既不会长期停留在「已通过」造成状态失真，
     * 也不会再出现"日期已过仍可取消"的问题（COMPLETED 为终态，取消会被状态机拦住）。</p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoCompleteExpired() {
        if (!autoCompleteEnabled || !sysConfigService.getBoolean("schedule.auto-complete", true)) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        // 只扫今天及更早、且仍处于已通过状态的单，避免全表扫描与误改未来预约
        List<LabReservation> list = reservationMapper.selectList(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStatus, ReservationStatus.APPROVED.name())
                .le(LabReservation::getReserveDate, today));
        int changed = 0;
        for (LabReservation reservation : list) {
            LocalDateTime endAt = LocalDateTime.of(reservation.getReserveDate(), reservation.getEndTime());
            if (now.isBefore(endAt)) {
                continue;
            }
            reservation.setStatus(ReservationStatus.COMPLETED.name());
            reservationMapper.updateById(reservation);
            changed++;
        }
        if (changed > 0) {
            // 预约状态影响驾驶舱统计，变更后必须让缓存失效
            statisticsService.evictStatCache();
            log.info("[预约] 过期预约自动完成，本次更新 {} 条", changed);
        }
    }

    /**
     * 预约开始前提醒（每 5 分钟扫描一次）
     *
     * <p>只扫当天、已通过、且尚未提醒过的预约；开始前 {提醒提前量} 分钟内推送，
     * 提醒后在预约单上打标记，任务重跑不会重复打扰。</p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void remindUpcoming() {
        if (!remindEnabled || !sysConfigService.getBoolean("schedule.remind", true)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<LabReservation> list = reservationMapper.selectList(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStatus, ReservationStatus.APPROVED.name())
                .eq(LabReservation::getReserveDate, now.toLocalDate())
                .eq(LabReservation::getReminderSent, 0));
        if (list.isEmpty()) {
            return;
        }
        // 已签到/已完成的预约不再提醒，避免"你已经到场了还催你"
        Set<Long> checkedIn = checkinMapper.selectList(new LambdaQueryWrapper<LabCheckin>()
                        .in(LabCheckin::getReservationId, list.stream().map(LabReservation::getId).toList())
                        .in(LabCheckin::getStatus, List.of(CheckinStatus.CHECKED_IN.name(),
                                CheckinStatus.COMPLETED.name())))
                .stream().map(LabCheckin::getReservationId).collect(Collectors.toSet());

        int sent = 0;
        for (LabReservation reservation : list) {
            if (checkedIn.contains(reservation.getId())) {
                continue;
            }
            LocalDateTime startAt = LocalDateTime.of(reservation.getReserveDate(), reservation.getStartTime());
            if (startAt.isBefore(now) || startAt.isAfter(now.plusMinutes(REMIND_MINUTES))) {
                continue;
            }
            noticeService.push(reservation.getStudentId(), "预约即将开始",
                    String.format("实验室「%s」今天 %s-%s 的预约将于 %s 开始，请提前到场并出示核销码签到。",
                            reservation.getLabName(), reservation.getStartTime(),
                            reservation.getEndTime(), startAt.format(DateTimeFormatter.ofPattern("HH:mm"))),
                    NoticeType.RESERVATION, reservation.getId());
            // 打上已提醒标记，任务重跑不会重复打扰
            reservation.setReminderSent(1);
            reservationMapper.updateById(reservation);
            sent++;
        }
        if (sent > 0) {
            log.info("[预约] 开始前提醒已推送 {} 条", sent);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO approve(ApprovalDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();
        // 并发串行化：先锁单行，避免并发审批导致同一单被重复流转、审批流水重复写入
        reservationMapper.lockReservationRow(dto.getReservationId());
        LabReservation reservation = reservationMapper.selectById(dto.getReservationId());
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约单不存在");
        }
        // 职责分离：不能审批自己提交的预约，否则审批控制形同虚设
        if (reservation.getStudentId().equals(current.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的预约");
        }
        // 教师提交的预约只能由管理员（超级管理员/实验室管理员）审批，其他教师无权批准
        if (isTeacherSubmitter(reservation.getStudentId()) && !isAdminApprover()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "教师提交的预约需由管理员审批");
        }
        ReservationStatus from = ReservationStatus.of(reservation.getStatus());
        boolean approve = "APPROVE".equalsIgnoreCase(dto.getAction());
        ReservationStatus to = approve
                ? ReservationStatus.APPROVED
                : ReservationStatus.REJECTED;

        // 状态机校验：仅 PENDING 可被审批流转
        if (!ReservationStatus.canTransition(from, to)) {
            throw new BusinessException(ResultCode.RESERVATION_STATUS_ERROR,
                    "仅待审核状态可审批，当前状态[" + from.getDesc() + "]");
        }

        // 审批通过同样需做一次设备故障复核，防止审批期间设备突发故障
        if (approve) {
            validateDeviceLock(reservation.getLabId(), null);
        }

        // 更新预约状态
        reservation.setStatus(to.name());
        reservation.setTeacherId(current.getUserId());
        reservation.setTeacherName(current.getRealName());
        if (!approve) {
            reservation.setRejectReason(dto.getComment());
        }
        reservationMapper.updateById(reservation);
        // 状态变更后让统计缓存失效，保证驾驶舱数据及时刷新
        statisticsService.evictStatCache();

        // 记录审批流水（支持多级审批，预留 approval_level）
        LabApproval approval = new LabApproval();
        approval.setReservationId(reservation.getId());
        approval.setApprovalLevel(1);
        approval.setApproverId(current.getUserId());
        approval.setApproverName(current.getRealName());
        approval.setAction(approve ? "APPROVE" : "REJECT");
        approval.setComment(dto.getComment());
        approval.setApproveTime(LocalDateTime.now());
        approvalMapper.insert(approval);

        // 审批通过：生成签到核销凭证，并推送通过/驳回通知，闭合业务链路
        if (approve) {
            checkinService.createForReservation(reservation);
            noticeService.push(reservation.getStudentId(), "预约审核通过",
                    String.format("实验室「%s」%s %s-%s 的预约已通过，"
                                    + "请在开放时段内到实验室出示核销码完成签到。",
                            reservation.getLabName(), reservation.getReserveDate(),
                            reservation.getStartTime(), reservation.getEndTime()),
                    NoticeType.RESERVATION, reservation.getId());
        } else {
            // 驳回同样释放了该时段容量，立即触发该实验室当日的候补递补
            publishSeatReleased(reservation.getLabId(), reservation.getReserveDate(), "预约驳回");
            noticeService.push(reservation.getStudentId(), "预约已被驳回",
                    String.format("实验室「%s」%s %s-%s 的预约被驳回%s",
                            reservation.getLabName(), reservation.getReserveDate(),
                            reservation.getStartTime(), reservation.getEndTime(),
                            StrUtil.isNotBlank(dto.getComment())
                                    ? "，原因：" + dto.getComment() : "。"),
                    NoticeType.RESERVATION, reservation.getId());
        }

        return convert(reservation);
    }

    @Override
    public com.lab.reservation.vo.BatchApprovalResultVO batchApprove(
            com.lab.reservation.dto.BatchApprovalDTO dto) {
        // 去重保序：前端勾选时可能重复传同一个 ID
        List<Long> ids = new ArrayList<>(new java.util.LinkedHashSet<>(dto.getReservationIds()));
        com.lab.reservation.vo.BatchApprovalResultVO result =
                new com.lab.reservation.vo.BatchApprovalResultVO();

        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        // 每单独立事务：部分失败时已成功的单据仍然保留
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (Long id : ids) {
            try {
                ApprovalDTO single = new ApprovalDTO();
                single.setReservationId(id);
                single.setAction(dto.getAction());
                single.setComment(dto.getComment());
                template.executeWithoutResult(status -> approve(single));
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BusinessException e) {
                result.addFailure(id, e.getMessage());
            } catch (Exception e) {
                // 非业务异常（如数据库瞬时故障）也只影响当前这一单
                log.warn("[预约] 批量审批单 {} 失败", id, e);
                result.addFailure(id, "系统异常，请稍后重试");
            }
        }
        log.info("[预约] 批量审批完成：动作={} 成功={} 失败={}",
                dto.getAction(), result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    @Override
    public com.lab.reservation.vo.RecurringResultVO submitRecurring(
            com.lab.reservation.dto.RecurringReservationDTO dto) {
        com.lab.reservation.vo.RecurringResultVO result =
                new com.lab.reservation.vo.RecurringResultVO();
        int weeks = dto.getRepeatWeeks() == null ? 1 : dto.getRepeatWeeks();

        org.springframework.transaction.support.TransactionTemplate template =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int i = 0; i < weeks; i++) {
            ReservationSubmitDTO single = new ReservationSubmitDTO();
            org.springframework.beans.BeanUtils.copyProperties(dto, single);
            LocalDate date = dto.getReserveDate().plusWeeks(i);
            single.setReserveDate(date);
            try {
                ReservationVO vo = template.execute(status -> submit(single));
                result.addSuccess(date,
                        vo == null ? null : vo.getId(),
                        vo == null ? null : vo.getReservationNo());
            } catch (BusinessException e) {
                result.addFailure(date, e.getMessage());
            } catch (Exception e) {
                log.warn("[预约] 周期预约 {} 失败", date, e);
                result.addFailure(date, "系统异常，请稍后重试");
            }
        }
        log.info("[预约] 周期预约完成：实验室={} 起始日={} 周数={} 成功={} 失败={}",
                dto.getLabId(), dto.getReserveDate(), weeks,
                result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    @Override
    public IPage<ReservationVO> pageMyReservations(IPage<ReservationVO> page, String status) {
        Long userId = SecurityUtil.getUserId();
        LambdaQueryWrapper<LabReservation> wrapper = new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStudentId, userId)
                .eq(StrUtil.isNotBlank(status), LabReservation::getStatus, status)
                .orderByDesc(LabReservation::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public IPage<ReservationVO> pageAll(IPage<ReservationVO> page, Long labId,
                                        String status, String keyword) {
        Set<Long> visible = dataScopeService.currentVisibleLabIds();
        LambdaQueryWrapper<LabReservation> wrapper = new LambdaQueryWrapper<LabReservation>()
                .eq(labId != null, LabReservation::getLabId, labId)
                // 数据权限：配置了可见实验室的用户只查授权范围；空集合直接查不到数据
                .in(visible != null, LabReservation::getLabId, visible == null || visible.isEmpty()
                        ? List.of(-1L) : visible)
                .eq(StrUtil.isNotBlank(status), LabReservation::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(LabReservation::getReservationNo, keyword)
                        .or().like(LabReservation::getStudentName, keyword)
                        .or().like(LabReservation::getLabName, keyword))
                .orderByDesc(LabReservation::getCreateTime);
        IPage<ReservationVO> result = queryPage(page, wrapper);
        // 逐行标注「当前登录人能否审批」，前端据此显隐按钮，
        // 避免教师看到教师单的审批按钮、点了才 40300
        boolean adminApprover = isAdminApprover();
        Long currentUserId = SecurityUtil.getUserId();
        Set<Long> teacherUserIds = adminApprover
                ? Set.of()
                : Set.copyOf(roleMapper.selectUserIdsByRoleCode(ROLE_TEACHER));
        result.getRecords().forEach(vo -> vo.setCanApprove(
                ReservationStatus.PENDING.name().equals(vo.getStatus())
                        && !vo.getStudentId().equals(currentUserId)
                        && (adminApprover || !teacherUserIds.contains(vo.getStudentId()))));
        return result;
    }

    @Override
    public ReservationVO getDetail(Long reservationId) {
        LabReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预约单不存在");
        }
        // 仅本人或具备预约管理权限的角色可查看详情，防止通过枚举/猜测 ID 越权读取他人预约
        LoginUser current = SecurityUtil.getLoginUser();
        boolean owner = reservation.getStudentId().equals(current.getUserId());
        if (!owner && !SecurityUtil.hasPermission("reservation:list")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该预约单");
        }
        return convert(reservation);
    }

    @Override
    public void exportExcel(jakarta.servlet.http.HttpServletResponse response,
                            Long labId, String status, String keyword) {
        // 复用管理端筛选逻辑
        LambdaQueryWrapper<LabReservation> wrapper = new LambdaQueryWrapper<LabReservation>()
                .eq(labId != null, LabReservation::getLabId, labId)
                .eq(StrUtil.isNotBlank(status), LabReservation::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(LabReservation::getReservationNo, keyword)
                        .or().like(LabReservation::getStudentName, keyword)
                        .or().like(LabReservation::getLabName, keyword))
                .orderByDesc(LabReservation::getCreateTime);
        // 导出上限：即便改用流式写，一次性 selectList 仍会把全部行读进内存，
        // 数据量大时容易把 768MB 堆打爆，故先计数拦截并引导用户缩小筛选范围
        Long total = reservationMapper.selectCount(wrapper);
        if (total != null && total > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.EXPORT_ERROR,
                    "待导出数据过多（" + total + " 条，上限 " + MAX_EXPORT_ROWS
                            + " 条），请先用实验室、状态或关键词缩小范围");
        }
        List<LabReservation> list = reservationMapper.selectList(wrapper);

        try (SXSSFWorkbook workbook = buildWorkbook(list)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = new String("预约记录导出_".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.ISO_8859_1)
                    + LocalDate.now() + ".xlsx";
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + fileName);
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.EXPORT_ERROR, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 构建预约导出工作簿（带样式表头）
     *
     * <p>使用 SXSSF 流式写入：内存中只保留窗口内若干行，其余刷到磁盘临时文件，
     * 相比 XSSF 全量驻留内存可显著降低导出时的堆占用。</p>
     */
    private SXSSFWorkbook buildWorkbook(List<LabReservation> list) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        Sheet sheet = workbook.createSheet("预约记录");

        // 样式：表头加粗 + 浅蓝底
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = {"预约单号", "实验室", "申请人", "审批人", "预约日期",
                "开始时间", "结束时间", "人数", "用途", "状态", "创建时间"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (LabReservation r : list) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(r.getReservationNo());
            row.createCell(1).setCellValue(r.getLabName());
            row.createCell(2).setCellValue(r.getStudentName());
            row.createCell(3).setCellValue(r.getTeacherName() == null ? "" : r.getTeacherName());
            row.createCell(4).setCellValue(r.getReserveDate().toString());
            row.createCell(5).setCellValue(r.getStartTime().toString());
            row.createCell(6).setCellValue(r.getEndTime().toString());
            row.createCell(7).setCellValue(r.getPeopleCount());
            row.createCell(8).setCellValue(r.getPurpose());
            row.createCell(9).setCellValue(ReservationStatus.of(r.getStatus()).getDesc());
            row.createCell(10).setCellValue(r.getCreateTime().toString());
        }
        // 固定列宽：SXSSF 下 autoSizeColumn 需要缓存全部行才能算宽度，
        // 会抵消流式写入的优势，故按内容长度预设列宽
        int[] widths = {22, 20, 12, 12, 12, 10, 10, 8, 28, 10, 20};
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        return workbook;
    }

    /* ==================== 复杂逻辑辅助方法 ==================== */

    /**
     * 开放时间段校验：请求的 [start,end) 必须被该实验室【对应星期】的某个启用时段完整包含。
     *
     * @return 完整包含该预约时段的开放时段（可能多条，用于后续的人数上限校验）
     */
    private List<LabTimeSlot> validateTimeWindow(Long labId, LocalDate date,
                                                 java.time.LocalTime start, java.time.LocalTime end) {
        int weekDay = date.getDayOfWeek().getValue();
        List<LabTimeSlot> slots = labTimeSlotMapper.selectList(
                new LambdaQueryWrapper<LabTimeSlot>()
                        .eq(LabTimeSlot::getLabId, labId)
                        .eq(LabTimeSlot::getWeekDay, weekDay)
                        .eq(LabTimeSlot::getStatus, 1));
        List<LabTimeSlot> matched = slots.stream()
                .filter(slot -> !slot.getStartTime().isAfter(start) && !slot.getEndTime().isBefore(end))
                .toList();
        if (matched.isEmpty()) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "预约时段[" + start + "-" + end + "]不在该实验室的开放时间段内");
        }
        return matched;
    }

    /**
     * 取所命中时段的单次人数上限：命中多个时段时取最严格的一条，避免通过嵌套时段绕过限制。
     *
     * @return 上限人数，0 表示不限
     */
    private int slotPeopleLimit(List<LabTimeSlot> matchedSlots) {
        return matchedSlots.stream()
                .map(LabTimeSlot::getMaxPeople)
                .filter(v -> v != null && v > 0)
                .min(Integer::compareTo)
                .orElse(0);
    }

    /**
     * 单次预约人数不得超过时段上限（0 不限）
     */
    private void validateSlotCapacity(int limit, int peopleCount) {
        if (limit > 0 && peopleCount > limit) {
            throw new BusinessException(ResultCode.RESERVATION_OVER_CAPACITY,
                    "预约人数超过该时段人数上限(" + limit + ")");
        }
    }

    /**
     * 校验本次申请人数是否还放得下：同一时段已有多单共享时，用「上限 - 已占用」得到剩余可约人数。
     *
     * @param name        上限名称（实验室容量 / 该时段人数上限），用于拼接提示
     * @param limit       人数上限（&gt; 0）
     * @param booked      目标区间内已占用的峰值人数
     * @param newPeople   本次申请人数
     */
    private void assertRemaining(String name, int limit, int booked, int newPeople) {
        int remaining = limit - booked;
        if (newPeople > remaining) {
            throw new BusinessException(ResultCode.RESERVATION_OVER_CAPACITY,
                    "该时段剩余可约人数 " + Math.max(remaining, 0) + " 人（" + name + " " + limit
                            + " 人），本次申请 " + newPeople + " 人，请调整人数或更换时段");
        }
    }

    /**
     * 设备故障锁定实验室预约：
     * <ol>
     *   <li>实验室下若存在故障设备（fault_status=1），整间实验室禁止预约（答辩亮点）</li>
     *   <li>若指定了使用设备，须确保设备属于该实验室、在用(1)且未故障</li>
     * </ol>
     */
    private void validateDeviceLock(Long labId, List<Long> useDeviceIds) {
        List<Device> faultDevices = deviceMapper.selectFaultDevicesByLabId(labId);
        if (!faultDevices.isEmpty()) {
            String names = faultDevices.stream()
                    .map(Device::getDeviceName).reduce((a, b) -> a + "、" + b).orElse("");
            throw new BusinessException(ResultCode.RESERVATION_LOCKED,
                    "实验室存在故障设备[" + names + "]，已锁定，暂不可预约");
        }
        if (useDeviceIds != null && !useDeviceIds.isEmpty()) {
            // 一次批量取回所选设备，避免逐个 selectById 产生 N 次往返
            List<Device> devices = deviceMapper.selectBatchIds(useDeviceIds);
            Map<Long, Device> deviceMap = devices.stream()
                    .collect(Collectors.toMap(Device::getId, d -> d));
            for (Long id : useDeviceIds) {
                Device device = deviceMap.get(id);
                if (device == null) {
                    throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在：" + id);
                }
                if (!device.getLabId().equals(labId)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "设备不属于该实验室：" + id);
                }
                if (device.getFaultStatus() != null && device.getFaultStatus() == 1) {
                    throw new BusinessException(ResultCode.DEVICE_REPAIR_LOCKED,
                            "所选设备[" + device.getDeviceName() + "]故障中");
                }
            }
        }
    }

    /**
     * 设备占用冲突校验：同一台设备在同一时段只能被一个有效预约使用。
     *
     * <p>复用「实验室 + 日期 + 时段」的冲突查询结果，在 Java 侧对 use_device_ids
     * 做交集判断——设备与预约之间是多对多逗号串，用 FIND_IN_SET 在 SQL 里判断
     * 既不可索引也不直观，而候选集本身只有同实验室同时段的少数几单。</p>
     *
     * @param overlapping  同实验室同日期同时段的有效预约
     * @param requestedIds 本次要使用的设备（可为空，表示不限设备）
     */
    private void assertDevicesFree(List<LabReservation> overlapping, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || overlapping == null
                || overlapping.isEmpty()) {
            return;
        }
        Set<Long> wanted = new java.util.HashSet<>(requestedIds);
        LabReservation conflict = null;
        List<Long> conflictIds = new ArrayList<>();
        for (LabReservation other : overlapping) {
            List<Long> used = parseDeviceIds(other.getUseDeviceIds());
            List<Long> hit = used.stream().filter(wanted::contains).toList();
            if (!hit.isEmpty()) {
                conflict = other;
                conflictIds = hit;
                break;
            }
        }
        if (conflict == null) {
            return;
        }
        List<Device> devices = deviceMapper.selectBatchIds(conflictIds);
        String names = devices.stream()
                .map(Device::getDeviceName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("、"));
        throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                "所选设备" + (names.isEmpty() ? "" : "[" + names + "]")
                        + "在该时段已被其他预约占用（单号 " + conflict.getReservationNo()
                        + "），请更换设备或时段");
    }

    /**
     * 逗号串转设备ID集合（非法片段直接忽略，历史脏数据不应导致改期/提交崩溃）
     */
    private List<Long> parseDeviceIds(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StrUtil.isBlank(part)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，不影响其余设备的校验
            }
        }
        return ids;
    }

    /**
     * 通用分页查询：先按实体分页，再映射为 VO 返回
     */
    private IPage<ReservationVO> queryPage(IPage<ReservationVO> voPage,
                                           LambdaQueryWrapper<LabReservation> wrapper) {
        Page<LabReservation> entityPage = new Page<>(voPage.getCurrent(), voPage.getSize());
        IPage<LabReservation> result = reservationMapper.selectPage(entityPage, wrapper);

        Page<ReservationVO> voResult = new Page<>(result.getCurrent(),
                result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    /**
     * 爽约限制校验：连续爽约满 3 次后会被限制预约 7 天，签到成功即解除
     */
    private void assertNotRestricted(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getNoShowBanUntil() != null
                && user.getNoShowBanUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "因连续爽约，你的预约权限已被限制至 "
                            + user.getNoShowBanUntil().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + "，按时签到可自动解除限制");
        }
    }

    /**
     * 生成预约单号：RSV + 时间戳 + 3位随机
     */
    private String generateNo() {
        LocalDateTime now = LocalDateTime.now();
        String ts = String.format("%04d%02d%02d%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
        return "RSV" + ts + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase();
    }

    /**
     * 当前登录人是否属于可审批教师单的管理员（超级管理员 / 实验室管理员）
     */
    private boolean isAdminApprover() {
        return SecurityUtil.hasRole(ROLE_SUPER_ADMIN) || SecurityUtil.hasRole(ROLE_LAB_ADMIN);
    }

    /**
     * 提交人是否教师：教师提交的预约需由管理员审批
     */
    private boolean isTeacherSubmitter(Long userId) {
        if (userId == null) {
            return false;
        }
        return roleMapper.selectRoleCodesByUserId(userId).contains(ROLE_TEACHER);
    }

    /**
     * 发布「容量已释放」事件，由候补监听器在事务提交后异步递补
     *
     * <p>事件属于增强路径：发布失败只记日志，不能影响取消/驳回/改期本身；
     * 候补队列仍有每 5 分钟一次的兜底扫描。</p>
     */
    private void publishSeatReleased(Long labId, LocalDate date, String reason) {
        if (labId == null || date == null) {
            return;
        }
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new LabSeatReleasedEvent(labId, date, reason));
            }
        } catch (Exception e) {
            log.warn("[预约] 容量释放事件发布失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /**
     * 向「有审批职责且有权批这单」的人推送待办消息：
     * 学生单 → 所有具备 reservation:approve 的人；
     * 教师单 → 仅管理员（剔除教师角色，与 approve 的权限规则保持一致）；
     * 同时排除提交人自己。
     */
    private void notifyApprovers(LabReservation reservation, String title, String content) {
        List<Long> ids = new ArrayList<>(roleMapper.selectUserIdsByPermission(PERM_APPROVE));
        if (isTeacherSubmitter(reservation.getStudentId())) {
            Set<Long> teacherIds = Set.copyOf(roleMapper.selectUserIdsByRoleCode(ROLE_TEACHER));
            ids.removeIf(teacherIds::contains);
        }
        ids.removeIf(id -> id.equals(reservation.getStudentId()));
        noticeService.pushAll(ids, title, content, NoticeType.RESERVATION, reservation.getId());
    }

    /**
     * 实体 -> VO（补充状态中文描述）
     */
    private ReservationVO convert(LabReservation reservation) {
        ReservationVO vo = new ReservationVO();
        BeanUtils.copyProperties(reservation, vo);
        ReservationStatus status = ReservationStatus.of(reservation.getStatus());
        vo.setStatusDesc(status == null ? "未知" : status.getDesc());
        return vo;
    }
}