package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabWaitlist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 候补队列 Mapper
 */
@Mapper
public interface LabWaitlistMapper extends BaseMapper<LabWaitlist> {
}
