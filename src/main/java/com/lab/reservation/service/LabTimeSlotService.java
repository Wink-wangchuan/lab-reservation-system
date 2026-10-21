package com.lab.reservation.service;

import com.lab.reservation.dto.LabTimeSlotDTO;
import com.lab.reservation.vo.AvailableSlotVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 实验室开放时段服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface LabTimeSlotService {

    /** 查询某实验室全部时段（按星期排序） */
    List<LabTimeSlotDTO> listByLab(Long labId);

    /** 批量为某实验室保存开放时段（先清后插） */
    void saveSlots(Long labId, List<LabTimeSlotDTO> slots);

    /** 查询某实验室某日的可预约时段（含占用标记） */
    List<AvailableSlotVO> availableSlots(Long labId, LocalDate date);
}