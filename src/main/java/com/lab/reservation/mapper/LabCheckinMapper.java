package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabCheckin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 预约签到核销单 Mapper
 *
 * @author LabWise
 * @since 1.1.0
 */
@Mapper
public interface LabCheckinMapper extends BaseMapper<LabCheckin> {

    /**
     * 对签到单行加排他锁，使核销/签退的「状态校验 + 更新」在并发下串行化，
     * 避免同一核销码被并发核销两次。
     */
    @Select("SELECT id FROM lab_checkin WHERE id = #{id} FOR UPDATE")
    Long lockCheckinRow(@Param("id") Long id);

    /**
     * 按核销码加锁（核销入口直接以码定位，锁住该行再校验状态）
     */
    @Select("SELECT id FROM lab_checkin WHERE checkin_code = #{code} LIMIT 1 FOR UPDATE")
    Long lockByCode(@Param("code") String code);
}
