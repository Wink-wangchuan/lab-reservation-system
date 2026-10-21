package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabTimeSlot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验室开放时间段 Mapper
 */
@Mapper
public interface LabTimeSlotMapper extends BaseMapper<LabTimeSlot> {
}