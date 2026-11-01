package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabClosure;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验室闭馆日历 Mapper
 */
@Mapper
public interface LabClosureMapper extends BaseMapper<LabClosure> {
}
