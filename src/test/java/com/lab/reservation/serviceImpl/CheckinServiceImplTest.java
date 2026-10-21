package com.lab.reservation.serviceImpl;

import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.CheckinVerifyDTO;
import com.lab.reservation.entity.LabCheckin;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.enums.CheckinStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabCheckinMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.CheckinVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到核销服务测试
 *
 * <p>重点锁定两条只靠人工验证很难覆盖全的规则：</p>
 * <ol>
 *   <li><b>30 分钟时间窗</b>：过早签到被拒、超时签到被拒、窗口内放行</li>
 *   <li><b>定时任务推进</b>：超时未签到 → 已过期并通知；已签到未签退 → 自动完成</li>
 * </ol>
 *
 * @author LabWise
 * @since 2.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("签到核销服务")
class CheckinServiceImplTest {

    private static final Long USER_ID = 20L;

    @Mock
    private LabCheckinMapper checkinMapper;
    @Mock
    private LabReservationMapper reservationMapper;
    @Mock
    private NoticeService noticeService;
    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private CheckinServiceImpl checkinService;

    private MockedStatic<SecurityUtil> securityUtil;

    /**
     * LambdaUpdateWrapper 解析 {@code LabReservation::getStatus} 依赖 MyBatis-Plus 的实体元数据，
     * 纯单元测试未启动容器，这里手动注册一次，否则会抛「can not find lambda cache」。
     */
    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                LabReservation.class);
    }

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        user.setUsername("李教授");
        user.setRealName("李教授");
        user.setStatus(1);
        user.setRoleCodes(new HashSet<>(Set.of("TEACHER")));
        user.setPermissions(new HashSet<>());

        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getLoginUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    @Nested
    @DisplayName("核销签到的时间窗")
    class VerifyWindow {

        @Test
        @DisplayName("核销码不存在")
        void codeNotFound() {
            when(checkinMapper.selectOne(any())).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> checkinService.verify(dto("000000")));

            assertEquals(ResultCode.CHECKIN_CODE_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("早于开始前 30 分钟，拒绝签到")
        void tooEarly() {
            // 预约在明天，无论当前几点都还没到可签到窗口
            LabCheckin checkin = pendingCheckin(LocalDate.now().plusDays(1),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(checkinMapper.selectOne(any())).thenReturn(checkin);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> checkinService.verify(dto("123456")));

            assertEquals(ResultCode.CHECKIN_NOT_IN_WINDOW.getCode(), e.getCode());
        }

        @Test
        @DisplayName("已超过结束时刻，拒绝签到")
        void expired() {
            // 预约在昨天，必然已结束
            LabCheckin checkin = pendingCheckin(LocalDate.now().minusDays(1),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(checkinMapper.selectOne(any())).thenReturn(checkin);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> checkinService.verify(dto("123456")));

            assertEquals(ResultCode.CHECKIN_EXPIRED.getCode(), e.getCode());
        }

        @Test
        @DisplayName("时间窗内核销成功")
        void withinWindow() {
            // 全天时段必然包含当前时刻，用于验证放行路径
            LabCheckin checkin = pendingCheckin(LocalDate.now(), LocalTime.MIN, LocalTime.MAX);
            when(checkinMapper.selectOne(any())).thenReturn(checkin);

            CheckinVO vo = checkinService.verify(dto("123456"));

            assertEquals(CheckinStatus.CHECKED_IN.name(), checkin.getStatus());
            assertEquals(USER_ID, checkin.getCheckinById());
            assertEquals("已签到", vo.getStatusDesc());
            verify(checkinMapper).updateById(any(LabCheckin.class));
        }

        @Test
        @DisplayName("已过期的核销码不能再次签到")
        void cannotRecheckExpired() {
            LabCheckin checkin = pendingCheckin(LocalDate.now(), LocalTime.MIN, LocalTime.MAX);
            checkin.setStatus(CheckinStatus.EXPIRED.name());
            when(checkinMapper.selectOne(any())).thenReturn(checkin);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> checkinService.verify(dto("123456")));

            assertEquals(ResultCode.RESERVATION_STATUS_ERROR.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("定时任务推进")
    class AutoClose {

        @Test
        @DisplayName("超时未签到置为过期并通知预约人")
        void expireUncheckedIn() {
            LabCheckin overdue = pendingCheckin(LocalDate.now().minusDays(1),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(checkinMapper.selectList(any())).thenReturn(List.of(overdue));

            checkinService.autoCloseExpired();

            assertEquals(CheckinStatus.EXPIRED.name(), overdue.getStatus());
            verify(noticeService).push(eq(overdue.getUserId()), eq("预约已过期"),
                    any(), any(), any());
        }

        @Test
        @DisplayName("已签到未签退置为已完成并同步预约单")
        void completeCheckedIn() {
            LabCheckin overdue = pendingCheckin(LocalDate.now().minusDays(1),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            overdue.setStatus(CheckinStatus.CHECKED_IN.name());
            when(checkinMapper.selectList(any())).thenReturn(List.of(overdue));

            checkinService.autoCloseExpired();

            assertEquals(CheckinStatus.COMPLETED.name(), overdue.getStatus());
            assertEquals(LocalDate.now().minusDays(1).atTime(10, 0),
                    overdue.getCheckoutTime(), "未签退的应回填结束时刻");
            verify(reservationMapper).update(any(), any());
        }

        @Test
        @DisplayName("尚未到结束时刻的记录保持原状")
        void notYetEnded() {
            LabCheckin ongoing = pendingCheckin(LocalDate.now().plusDays(1),
                    LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(checkinMapper.selectList(any())).thenReturn(List.of(ongoing));

            checkinService.autoCloseExpired();

            assertEquals(CheckinStatus.SIGN_IN_PENDING.name(), ongoing.getStatus());
            verify(checkinMapper, times(0)).updateById(any(LabCheckin.class));
        }
    }

    // ==================== 测试数据构造 ====================

    private CheckinVerifyDTO dto(String code) {
        CheckinVerifyDTO dto = new CheckinVerifyDTO();
        dto.setCheckinCode(code);
        return dto;
    }

    private LabCheckin pendingCheckin(LocalDate date, LocalTime start, LocalTime end) {
        LabCheckin checkin = new LabCheckin();
        checkin.setId(1L);
        checkin.setCheckinCode("123456");
        checkin.setReservationId(100L);
        checkin.setReservationNo("RSV20260101000000CCCC");
        checkin.setLabId(101L);
        checkin.setLabName("第一计算机实验室");
        checkin.setUserId(30L);
        checkin.setUserName("张三");
        checkin.setReserveDate(date);
        checkin.setStartTime(start);
        checkin.setEndTime(end);
        checkin.setStatus(CheckinStatus.SIGN_IN_PENDING.name());
        return checkin;
    }
}
