package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.OccupancyCalculator;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.LabTimeSlotDTO;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.service.LabTimeSlotService;
import com.lab.reservation.vo.AvailableSlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 实验室开放时段服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：可预约时段查询</b></p>
 * 学生端选择某实验室某日后，系统取出该实验室<b>当日（按星期）启用时段</b>，
 * 复用区间相交判定 + 并发人数峰值计算每个时段已占用的人数，
 * 进而给出剩余可约人数并标记 {@code occupied}（仅当已约满），
 * 帮助学生判断该时段还能不能带人过来。
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LabTimeSlotServiceImpl implements LabTimeSlotService {

    private final LabTimeSlotMapper slotMapper;
    private final LabReservationMapper reservationMapper;
    private final LabInfoMapper labInfoMapper;

    /** 不占用时段的状态 */
    private static final List<String> BANNED =
            List.of(ReservationStatus.CANCELLED.name(),
                    ReservationStatus.REJECTED.name(),
                    ReservationStatus.DRAFT.name());

    @Override
    public List<LabTimeSlotDTO> listByLab(Long labId) {
        return slotMapper.selectList(new LambdaQueryWrapper<LabTimeSlot>()
                        .eq(LabTimeSlot::getLabId, labId)
                        .orderByAsc(LabTimeSlot::getWeekDay)
                        .orderByAsc(LabTimeSlot::getStartTime))
                .stream().map(slot -> {
                    LabTimeSlotDTO dto = new LabTimeSlotDTO();
                    BeanUtils.copyProperties(slot, dto);
                    return dto;
                }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSlots(Long labId, List<LabTimeSlotDTO> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "时段列表不能为空");
        }
        // 时段合法性：开始 < 结束
        for (LabTimeSlotDTO dto : slots) {
            if (!dto.getStartTime().isBefore(dto.getEndTime())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "时段[" + dto.getSlotName() + "]结束时间必须晚于开始时间");
            }
            if (dto.getWeekDay() == null || dto.getWeekDay() < 1 || dto.getWeekDay() > 7) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "星期取值须在1-7之间");
            }
        }
        // 先清空该实验室原有时段，再批量写入（整体覆盖式配置）
        slotMapper.delete(new LambdaQueryWrapper<LabTimeSlot>()
                .eq(LabTimeSlot::getLabId, labId));
        slots.forEach(dto -> {
            LabTimeSlot slot = new LabTimeSlot();
            BeanUtils.copyProperties(dto, slot);
            slot.setId(null);
            slot.setLabId(labId);
            if (slot.getStatus() == null) {
                slot.setStatus(1);
            }
            slotMapper.insert(slot);
        });
    }

    @Override
    public List<AvailableSlotVO> availableSlots(Long labId, LocalDate date) {
        int weekDay = date.getDayOfWeek().getValue();
        // 当日（对应星期）启用时段
        List<LabTimeSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<LabTimeSlot>()
                .eq(LabTimeSlot::getLabId, labId)
                .eq(LabTimeSlot::getWeekDay, weekDay)
                .eq(LabTimeSlot::getStatus, 1)
                .orderByAsc(LabTimeSlot::getStartTime));
        if (slots.isEmpty()) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_ALLOW, "该实验室当日未配置开放时段");
        }

        // 一次批量查出覆盖全部时段范围的有效预约，避免逐时段 N+1 查询
        // 包络区间 [最早的时段开始, 最晚的时段结束] 必定包含所有可能与任一时段重叠的预约
        LocalTime rangeStart = slots.get(0).getStartTime();
        LocalTime rangeEnd = slots.stream()
                .map(LabTimeSlot::getEndTime)
                .max(LocalTime::compareTo)
                .orElse(rangeStart);
        List<LabReservation> dayReservations = reservationMapper.selectConflictingReservations(
                labId, date, rangeStart, rangeEnd, null, BANNED);

        // 剩余可约人数取决于「实验室容量」与「该时段人数上限」中更严格的一个
        LabInfo lab = labInfoMapper.selectById(labId);
        int labCapacity = lab == null || lab.getCapacity() == null ? 0 : lab.getCapacity();

        // 对每个时段在内存中复用区间判定 + 并发峰值统计
        return slots.stream().map(slot -> {
            AvailableSlotVO vo = new AvailableSlotVO();
            vo.setId(slot.getId());
            vo.setSlotName(slot.getSlotName());
            vo.setStartTime(slot.getStartTime());
            vo.setEndTime(slot.getEndTime());
            vo.setMaxPeople(slot.getMaxPeople());

            int booked = OccupancyCalculator.peakPeople(dayReservations,
                    slot.getStartTime(), slot.getEndTime());
            vo.setBookedPeople(booked);
            vo.setReservationCount(dayReservations.stream()
                    .filter(r -> r.getStartTime().isBefore(slot.getEndTime())
                            && r.getEndTime().isAfter(slot.getStartTime()))
                    .count());

            int limit = peopleLimit(labCapacity, slot.getMaxPeople());
            if (limit > 0) {
                vo.setRemainingPeople(Math.max(0, limit - booked));
                vo.setOccupied(vo.getRemainingPeople() == 0);
            } else {
                // 0 表示不限人数，永远不会「已满」
                vo.setRemainingPeople(null);
                vo.setOccupied(false);
            }
            return vo;
        }).toList();
    }

    /**
     * 时段可容纳的人数：实验室容量与时段人数上限取更严格的一个，0 表示不限
     */
    private int peopleLimit(int labCapacity, Integer slotMaxPeople) {
        int slotMax = slotMaxPeople == null ? 0 : slotMaxPeople;
        if (labCapacity > 0 && slotMax > 0) {
            return Math.min(labCapacity, slotMax);
        }
        return labCapacity > 0 ? labCapacity : slotMax;
    }
}