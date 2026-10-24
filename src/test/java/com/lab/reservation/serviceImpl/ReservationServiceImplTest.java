package com.lab.reservation.serviceImpl;

import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabApproval;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
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
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.ReservationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约服务核心规则测试
 *
 * <p>覆盖两条最容易出错的业务链：</p>
 * <ol>
 *   <li><b>提交校验链</b>：六道校验必须按顺序拦截，任何一道失效都会产生脏数据</li>
 *   <li><b>审批权限规则</b>：不能自审、教师单只能管理员审批、仅待审核可流转</li>
 * </ol>
 *
 * <p>使用 Mockito 隔离数据库，只验证业务规则本身，不依赖 MySQL 与 Redis。</p>
 *
 * @author LabWise
 * @since 2.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预约服务：校验链与审批规则")
class ReservationServiceImplTest {

    private static final Long LAB_ID = 101L;
    private static final Long STUDENT_ID = 10L;
    private static final Long TEACHER_ID = 99L;

    @Mock
    private LabInfoMapper labInfoMapper;
    @Mock
    private LabTimeSlotMapper labTimeSlotMapper;
    @Mock
    private LabReservationMapper reservationMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private LabApprovalMapper approvalMapper;
    @Mock
    private StatisticsService statisticsService;
    @Mock
    private CheckinService checkinService;
    @Mock
    private NoticeService noticeService;
    @Mock
    private SysRoleMapper roleMapper;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private MockedStatic<SecurityUtil> securityUtil;
    private LoginUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = loginUser(STUDENT_ID, "张三", "STUDENT");
        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getLoginUser).thenReturn(currentUser);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    // ==================== 提交校验链 ====================

    @Nested
    @DisplayName("提交预约的六重校验")
    class SubmitValidation {

        @Test
        @DisplayName("实验室不存在")
        void labNotFound() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(validDto()));

            assertEquals(ResultCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("实验室被禁止预约")
        void reserveDisabled() {
            LabInfo lab = openLab(60);
            lab.setEnableReserve(0);
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(lab);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(validDto()));

            assertEquals(ResultCode.RESERVATION_NOT_ALLOW.getCode(), e.getCode());
        }

        @Test
        @DisplayName("实验室未开放")
        void labClosed() {
            LabInfo lab = openLab(60);
            lab.setOpenStatus(0);
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(lab);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(validDto()));

            assertEquals(ResultCode.RESERVATION_NOT_ALLOW.getCode(), e.getCode());
        }

        @Test
        @DisplayName("预约日期早于今天")
        void dateInPast() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            ReservationSubmitDTO dto = validDto();
            dto.setReserveDate(LocalDate.now().minusDays(1));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(dto));

            assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("结束时间不晚于开始时间")
        void invalidTimeRange() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            ReservationSubmitDTO dto = validDto();
            dto.setStartTime(LocalTime.of(10, 0));
            dto.setEndTime(LocalTime.of(10, 0));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(dto));

            assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("时段不在开放时段内")
        void outsideOpenSlot() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            // 当天只开放 08:00-09:40，却申请 10:00-11:40
            when(labTimeSlotMapper.selectList(any())).thenReturn(List.of(slot(8, 0, 9, 40)));

            ReservationSubmitDTO dto = validDto();
            dto.setStartTime(LocalTime.of(10, 0));
            dto.setEndTime(LocalTime.of(11, 40));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(dto));

            assertEquals(ResultCode.RESERVATION_NOT_ALLOW.getCode(), e.getCode());
        }

        @Test
        @DisplayName("人数超过实验室容量")
        void overCapacity() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(20));
            when(labTimeSlotMapper.selectList(any())).thenReturn(List.of(slot(8, 0, 9, 40)));

            ReservationSubmitDTO dto = validDto();
            dto.setPeopleCount(30);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(dto));

            assertEquals(ResultCode.RESERVATION_OVER_CAPACITY.getCode(), e.getCode());
        }

        @Test
        @DisplayName("实验室存在故障设备时整间锁定")
        void lockedByFaultDevice() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            when(labTimeSlotMapper.selectList(any())).thenReturn(List.of(slot(8, 0, 9, 40)));
            Device fault = new Device();
            fault.setDeviceName("示波器");
            when(deviceMapper.selectFaultDevicesByLabId(LAB_ID)).thenReturn(List.of(fault));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(validDto()));

            assertEquals(ResultCode.RESERVATION_LOCKED.getCode(), e.getCode());
        }

        @Test
        @DisplayName("时段已被占用时报时间冲突")
        void timeConflict() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            when(labTimeSlotMapper.selectList(any())).thenReturn(List.of(slot(8, 0, 9, 40)));
            when(deviceMapper.selectFaultDevicesByLabId(LAB_ID)).thenReturn(List.of());
            LabReservation existing = new LabReservation();
            existing.setReservationNo("RSV20260101000000AAAA");
            existing.setStatus(ReservationStatus.APPROVED.name());
            when(reservationMapper.selectConflictingReservations(
                    any(), any(), any(), any(), any(), any())).thenReturn(List.of(existing));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.submit(validDto()));

            assertEquals(ResultCode.RESERVATION_CONFLICT.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("RSV20260101000000AAAA"));
        }

        @Test
        @DisplayName("校验全部通过时落库为待审核并通知审批人")
        void submitSuccess() {
            when(labInfoMapper.selectById(LAB_ID)).thenReturn(openLab(60));
            when(labTimeSlotMapper.selectList(any())).thenReturn(List.of(slot(8, 0, 9, 40)));
            when(deviceMapper.selectFaultDevicesByLabId(LAB_ID)).thenReturn(List.of());
            when(reservationMapper.selectConflictingReservations(
                    any(), any(), any(), any(), any(), any())).thenReturn(List.of());
            // 提交人不是教师；审批人列表为空（只验证推送被调用，不关心收件人）
            when(roleMapper.selectRoleCodesByUserId(STUDENT_ID)).thenReturn(List.of("STUDENT"));
            when(roleMapper.selectUserIdsByPermission(any())).thenReturn(List.of());

            ReservationVO vo = reservationService.submit(validDto());

            ArgumentCaptor<LabReservation> captor = ArgumentCaptor.forClass(LabReservation.class);
            verify(reservationMapper).insert(captor.capture());
            LabReservation saved = captor.getValue();

            assertEquals(ReservationStatus.PENDING.name(), saved.getStatus());
            assertTrue(saved.getReservationNo().startsWith("RSV"));
            assertEquals(STUDENT_ID, saved.getStudentId());
            assertEquals("待审核", vo.getStatusDesc());
            // 热度累加与站内通知都必须发生
            verify(statisticsService).recordReservationBooking(LAB_ID, "第一计算机实验室");
            verify(noticeService).push(any(), any(), any(), any(), any());
        }
    }

    // ==================== 审批权限规则 ====================

    @Nested
    @DisplayName("审批规则")
    class ApprovalRules {

        @Test
        @DisplayName("不能审批自己提交的预约")
        void cannotApproveOwn() {
            LabReservation own = pendingReservation(STUDENT_ID);
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(own);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.approve(approveDto(1L, "APPROVE")));

            assertEquals(ResultCode.FORBIDDEN.getCode(), e.getCode());
            verify(checkinService, never()).createForReservation(any());
        }

        @Test
        @DisplayName("教师提交的预约不能被普通教师审批")
        void teacherCannotApproveTeacher() {
            LabReservation teacherOrder = pendingReservation(TEACHER_ID);
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(teacherOrder);
            when(roleMapper.selectRoleCodesByUserId(TEACHER_ID)).thenReturn(List.of("TEACHER"));
            // 当前登录人是普通教师（无管理员角色）
            securityUtil.when(() -> SecurityUtil.hasRole("SUPER_ADMIN")).thenReturn(false);
            securityUtil.when(() -> SecurityUtil.hasRole("LAB_ADMIN")).thenReturn(false);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.approve(approveDto(1L, "APPROVE")));

            assertEquals(ResultCode.FORBIDDEN.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("管理员"));
        }

        @Test
        @DisplayName("仅待审核状态可审批")
        void onlyPendingCanBeApproved() {
            LabReservation approved = pendingReservation(TEACHER_ID);
            approved.setStatus(ReservationStatus.APPROVED.name());
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(approved);
            when(roleMapper.selectRoleCodesByUserId(TEACHER_ID)).thenReturn(List.of("TEACHER"));
            securityUtil.when(() -> SecurityUtil.hasRole("SUPER_ADMIN")).thenReturn(true);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.approve(approveDto(1L, "APPROVE")));

            assertEquals(ResultCode.RESERVATION_STATUS_ERROR.getCode(), e.getCode());
        }

        @Test
        @DisplayName("管理员审批通过后生成签到凭证")
        void adminApproveCreatesCheckin() {
            LabReservation order = pendingReservation(TEACHER_ID);
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(order);
            when(roleMapper.selectRoleCodesByUserId(TEACHER_ID)).thenReturn(List.of("TEACHER"));
            securityUtil.when(() -> SecurityUtil.hasRole("SUPER_ADMIN")).thenReturn(true);
            when(deviceMapper.selectFaultDevicesByLabId(LAB_ID)).thenReturn(List.of());

            ReservationVO vo = reservationService.approve(approveDto(1L, "APPROVE"));

            assertEquals(ReservationStatus.APPROVED.name(), order.getStatus());
            assertEquals("已通过", vo.getStatusDesc());
            verify(checkinService).createForReservation(order);
            verify(approvalMapper).insert(any(LabApproval.class));
        }

        @Test
        @DisplayName("驳回时记录驳回原因且不生成签到凭证")
        void rejectRecordsReason() {
            LabReservation order = pendingReservation(TEACHER_ID);
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(order);
            when(roleMapper.selectRoleCodesByUserId(TEACHER_ID)).thenReturn(List.of("TEACHER"));
            securityUtil.when(() -> SecurityUtil.hasRole("SUPER_ADMIN")).thenReturn(true);

            reservationService.approve(approveDto(1L, "REJECT"));

            assertEquals(ReservationStatus.REJECTED.name(), order.getStatus());
            assertEquals("时段不合适", order.getRejectReason());
            verify(checkinService, never()).createForReservation(any());
        }
    }

    // ==================== 取消规则 ====================

    @Nested
    @DisplayName("取消规则")
    class CancelRules {

        @Test
        @DisplayName("已通过的预约一旦开始（日期已过）不允许取消")
        void cannotCancelStartedApproved() {
            LabReservation past = pendingReservation(STUDENT_ID);
            past.setStatus(ReservationStatus.APPROVED.name());
            past.setReserveDate(LocalDate.now().minusDays(1));
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(past);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reservationService.cancel(1L, "临时有事"));

            assertEquals(ResultCode.RESERVATION_STATUS_ERROR.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("无法取消"));
            // 状态不能被改写，签到凭证也不能被联动取消
            assertEquals(ReservationStatus.APPROVED.name(), past.getStatus());
            verify(reservationMapper, never()).updateById(any(LabReservation.class));
            verify(checkinService, never()).cancelByReservation(any(), any());
        }

        @Test
        @DisplayName("已通过但尚未开始的预约可以取消")
        void canCancelNotStartedApproved() {
            LabReservation future = pendingReservation(STUDENT_ID);
            future.setStatus(ReservationStatus.APPROVED.name());
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(future);

            reservationService.cancel(1L, "临时有事");

            assertEquals(ReservationStatus.CANCELLED.name(), future.getStatus());
            assertEquals("临时有事", future.getCancelReason());
            verify(checkinService).cancelByReservation(1L, "临时有事");
        }

        @Test
        @DisplayName("待审核的预约即使日期已过仍可撤回")
        void pendingCanAlwaysBeWithdrawn() {
            LabReservation stale = pendingReservation(STUDENT_ID);
            stale.setReserveDate(LocalDate.now().minusDays(1));
            when(reservationMapper.lockReservationRow(1L)).thenReturn(1L);
            when(reservationMapper.selectById(1L)).thenReturn(stale);

            reservationService.cancel(1L, "时间已过，撤回");

            assertEquals(ReservationStatus.CANCELLED.name(), stale.getStatus());
        }
    }

    // ==================== 过期预约自动完成 ====================

    @Nested
    @DisplayName("过期预约自动完成")
    class AutoCompleteExpired {

        @Test
        @DisplayName("结束时刻已过的已通过预约自动置为已完成")
        void completeExpiredApproved() {
            LabReservation expired = pendingReservation(STUDENT_ID);
            expired.setStatus(ReservationStatus.APPROVED.name());
            expired.setReserveDate(LocalDate.now().minusDays(1));
            // 当天但时段尚未结束的预约不应被推进
            LabReservation ongoing = pendingReservation(STUDENT_ID);
            ongoing.setStatus(ReservationStatus.APPROVED.name());
            ongoing.setReserveDate(LocalDate.now());
            ongoing.setEndTime(LocalTime.of(23, 59, 59));
            when(reservationMapper.selectList(any())).thenReturn(List.of(expired, ongoing));

            reservationService.autoCompleteExpired();

            assertEquals(ReservationStatus.COMPLETED.name(), expired.getStatus());
            assertEquals(ReservationStatus.APPROVED.name(), ongoing.getStatus());
            verify(reservationMapper).updateById(expired);
            verify(statisticsService).evictStatCache();
        }

        @Test
        @DisplayName("没有过期预约时不写库、不失效统计缓存")
        void nothingToDo() {
            LabReservation future = pendingReservation(STUDENT_ID);
            future.setStatus(ReservationStatus.APPROVED.name());
            future.setReserveDate(LocalDate.now().plusDays(1));
            when(reservationMapper.selectList(any())).thenReturn(List.of(future));

            reservationService.autoCompleteExpired();

            assertEquals(ReservationStatus.APPROVED.name(), future.getStatus());
            verify(reservationMapper, never()).updateById(any(LabReservation.class));
            verify(statisticsService, never()).evictStatCache();
        }
    }

    // ==================== 测试数据构造 ====================

    private LoginUser loginUser(Long id, String realName, String roleCode) {
        LoginUser user = new LoginUser();
        user.setUserId(id);
        user.setUsername(realName);
        user.setRealName(realName);
        user.setStatus(1);
        user.setRoleCodes(new HashSet<>(Set.of(roleCode)));
        user.setPermissions(new HashSet<>());
        return user;
    }

    private ReservationSubmitDTO validDto() {
        ReservationSubmitDTO dto = new ReservationSubmitDTO();
        dto.setLabId(LAB_ID);
        dto.setReserveDate(LocalDate.now().plusDays(1));
        dto.setStartTime(LocalTime.of(8, 0));
        dto.setEndTime(LocalTime.of(9, 40));
        dto.setPurpose("完成课程实验");
        dto.setPeopleCount(30);
        return dto;
    }

    private LabInfo openLab(int capacity) {
        LabInfo lab = new LabInfo();
        lab.setId(LAB_ID);
        lab.setLabName("第一计算机实验室");
        lab.setCapacity(capacity);
        lab.setOpenStatus(1);
        lab.setEnableReserve(1);
        return lab;
    }

    private LabTimeSlot slot(int startHour, int startMinute, int endHour, int endMinute) {
        LabTimeSlot slot = new LabTimeSlot();
        slot.setLabId(LAB_ID);
        slot.setWeekDay(1);
        slot.setSlotName("上午第一节");
        slot.setStartTime(LocalTime.of(startHour, startMinute));
        slot.setEndTime(LocalTime.of(endHour, endMinute));
        slot.setStatus(1);
        return slot;
    }

    private LabReservation pendingReservation(Long submitterId) {
        LabReservation reservation = new LabReservation();
        reservation.setId(1L);
        reservation.setReservationNo("RSV20260101000000BBBB");
        reservation.setLabId(LAB_ID);
        reservation.setLabName("第一计算机实验室");
        reservation.setStudentId(submitterId);
        reservation.setStudentName("提交人");
        reservation.setReserveDate(LocalDate.now().plusDays(1));
        reservation.setStartTime(LocalTime.of(8, 0));
        reservation.setEndTime(LocalTime.of(9, 40));
        reservation.setStatus(ReservationStatus.PENDING.name());
        return reservation;
    }

    private ApprovalDTO approveDto(Long reservationId, String action) {
        ApprovalDTO dto = new ApprovalDTO();
        dto.setReservationId(reservationId);
        dto.setAction(action);
        dto.setComment("时段不合适");
        return dto;
    }
}
