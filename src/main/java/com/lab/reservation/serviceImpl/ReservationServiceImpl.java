package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabApproval;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabApprovalMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.CheckinService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.ReservationService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 预约业务服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：时间冲突智能校验算法</b></p>
 * 两个时间区间 [a,b) 与 [c,d) 冲突的充要条件是存在交集：
 * <pre>a &lt; d AND b &gt; c</pre>
 * 该判定在 Mapper 层通过 {@code selectConflictingReservations} 完成，
 * 并结合预约状态过滤（草稿/取消/驳回不占用）、设备故障锁定、开放时段校验构成三重防线。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationVO submit(ReservationSubmitDTO dto) {
        LoginUser current = SecurityUtil.getLoginUser();

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
        validateTimeWindow(lab.getId(), date, dto.getStartTime(), dto.getEndTime());
        // 2.2 容量校验
        if (dto.getPeopleCount() > lab.getCapacity()) {
            throw new BusinessException(ResultCode.RESERVATION_OVER_CAPACITY,
                    "预约人数超过实验室容量(" + lab.getCapacity() + ")");
        }

        // ========== 3. 设备故障锁定实验室校验 ==========
        validateDeviceLock(lab.getId(), dto.getUseDeviceIds());

        // ========== 4. 时间冲突智能校验（答辩重点） ==========
        List<LabReservation> conflicts = reservationMapper.selectConflictingReservations(
                lab.getId(), date, dto.getStartTime(), dto.getEndTime(), null, BANNED_STATUS);
        if (!conflicts.isEmpty()) {
            LabReservation first = conflicts.get(0);
            throw new BusinessException(ResultCode.RESERVATION_CONFLICT,
                    "时间冲突：该时段已被占用（单号 " + first.getReservationNo()
                            + "，状态 " + ReservationStatus.of(first.getStatus()).getDesc() + "）");
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
        boolean wasPending = from == ReservationStatus.PENDING;
        reservation.setStatus(ReservationStatus.CANCELLED.name());
        reservation.setCancelReason(reason);
        reservationMapper.updateById(reservation);
        // 联动取消对应签到凭证，闭合状态一致性
        checkinService.cancelByReservation(reservation.getId(), reason);
        // 状态变更后让统计缓存失效，保证驾驶舱数据及时刷新
        statisticsService.evictStatCache();
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
        LambdaQueryWrapper<LabReservation> wrapper = new LambdaQueryWrapper<LabReservation>()
                .eq(labId != null, LabReservation::getLabId, labId)
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
        List<LabReservation> list = reservationMapper.selectList(wrapper);

        try (XSSFWorkbook workbook = buildWorkbook(list)) {
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
     * 构建入职预约导出工作簿（带样式表头）
     */
    private XSSFWorkbook buildWorkbook(List<LabReservation> list) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("预约记录");

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
        // 自适应列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        return workbook;
    }

    /* ==================== 复杂逻辑辅助方法 ==================== */

    /**
     * 开放时间段校验：请求的 [start,end) 必须被该实验室【对应星期】的某个启用时段完整包含。
     */
    private void validateTimeWindow(Long labId, LocalDate date,
                                    java.time.LocalTime start, java.time.LocalTime end) {
        int weekDay = date.getDayOfWeek().getValue();
        List<LabTimeSlot> slots = labTimeSlotMapper.selectList(
                new LambdaQueryWrapper<LabTimeSlot>()
                        .eq(LabTimeSlot::getLabId, labId)
                        .eq(LabTimeSlot::getWeekDay, weekDay)
                        .eq(LabTimeSlot::getStatus, 1));
        boolean matches = slots.stream().anyMatch(slot ->
                !slot.getStartTime().isAfter(start) && !slot.getEndTime().isBefore(end));
        if (!matches) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW,
                    "预约时段[" + start + "-" + end + "]不在该实验室的开放时间段内");
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
            for (Long id : useDeviceIds) {
                Device device = deviceMapper.selectById(id);
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