package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验室 Mapper
 */
@Mapper
public interface LabInfoMapper extends BaseMapper<LabInfo> {
}