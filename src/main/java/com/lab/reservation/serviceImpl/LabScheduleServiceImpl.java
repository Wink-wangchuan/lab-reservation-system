package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.LabScheduleService;
import com.lab.reservation.vo.WeekScheduleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;

/**
 * 实验室周日程实现
 *
 * <p>按「周一起始的 7 天」聚合开放时段与预约单，供学生选时段、教师排课查看。
 * 草稿/取消/驳回状态不进入日程，避免把无效预约显示成占用。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Service
@RequiredArgsConstructor
public class LabScheduleServiceImpl implements LabScheduleService {

    private final LabInfoMapper labInfoMapper;
    private final LabTimeSlotMapper slotMapper;
    private final LabReservationMapper reservationMapper;

    private static final List<String> IGNORED_STATUS = List.of(
            ReservationStatus.DRAFT.name(),
            ReservationStatus.CANCELLED.name(),
            ReservationStatus.REJECTED.name());

    @Override
    public WeekScheduleVO week(Long labId, LocalDate startDate) {
        LabInfo lab = labId == null ? null : labInfoMapper.selectById(labId);
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "实验室不存在");
        }
        LocalDate start = startDate == null ? LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : startDate;
        LocalDate end = start.plusDays(6);

        WeekScheduleVO vo = new WeekScheduleVO();
        vo.setLabId(lab.getId());
        vo.setLabName(lab.getLabName());
        vo.setStartDate(start);
        vo.setEndDate(end);

        List<LabTimeSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<LabTimeSlot>()
                .eq(LabTimeSlot::getLabId, labId)
                .eq(LabTimeSlot::getStatus, 1)
                .orderByAsc(LabTimeSlot::getStartTime));
        List<LabReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<LabReservation>()
                        .eq(LabReservation::getLabId, labId)
                        .between(LabReservation::getReserveDate, start, end)
                        .notIn(LabReservation::getStatus, IGNORED_STATUS)
                        .orderByAsc(LabReservation::getStartTime));

        Long currentUserId = SecurityUtil.getUserId();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            WeekScheduleVO.Day day = new WeekScheduleVO.Day();
            day.setDate(date);
            day.setWeekday(date.getDayOfWeek().getValue());
            day.setSlots(slots.stream()
                    .filter(s -> s.getWeekDay() != null && s.getWeekDay() == day.getWeekday())
                    .map(this::toSlot)
                    .toList());
            day.setReservations(reservations.stream()
                    .filter(r -> date.equals(r.getReserveDate()))
                    .sorted(Comparator.comparing(LabReservation::getStartTime))
                    .map(r -> toItem(r, currentUserId))
                    .toList());
            vo.getDays().add(day);
        }
        return vo;
    }

    private WeekScheduleVO.Slot toSlot(LabTimeSlot slot) {
        WeekScheduleVO.Slot vo = new WeekScheduleVO.Slot();
        vo.setId(slot.getId());
        vo.setSlotName(slot.getSlotName());
        vo.setStartTime(slot.getStartTime());
        vo.setEndTime(slot.getEndTime());
        vo.setMaxPeople(slot.getMaxPeople());
        return vo;
    }

    private WeekScheduleVO.Item toItem(LabReservation reservation, Long currentUserId) {
        WeekScheduleVO.Item item = new WeekScheduleVO.Item();
        item.setId(reservation.getId());
        item.setReservationNo(reservation.getReservationNo());
        item.setStudentName(reservation.getStudentName());
        item.setStartTime(reservation.getStartTime());
        item.setEndTime(reservation.getEndTime());
        item.setPeopleCount(reservation.getPeopleCount());
        item.setPurpose(reservation.getPurpose());
        item.setStatus(reservation.getStatus());
        ReservationStatus status = ReservationStatus.of(reservation.getStatus());
        item.setStatusDesc(status == null ? "未知" : status.getDesc());
        item.setMine(currentUserId != null && currentUserId.equals(reservation.getStudentId()));
        return item;
    }
}
