package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约审批记录 Mapper
 */
@Mapper
public interface LabApprovalMapper extends BaseMapper<LabApproval> {
}