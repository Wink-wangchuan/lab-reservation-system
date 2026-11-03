package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.SysUserLab;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-实验室数据权限 Mapper
 *
 * @author LabWise
 * @since 4.6.0
 */
@Mapper
public interface SysUserLabMapper extends BaseMapper<SysUserLab> {
}
