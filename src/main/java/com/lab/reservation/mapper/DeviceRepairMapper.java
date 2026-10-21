package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.DeviceRepair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备报修单 Mapper
 *
 * <p>除基础 CRUD 外，提供并发场景下的行级排他锁：报修提交需要「先查进行中工单、后插入」，
 * 报修流转需要「先校验状态机、后更新」，两者都必须串行化才能保证并发正确性。</p>
 */
@Mapper
public interface DeviceRepairMapper extends BaseMapper<DeviceRepair> {

    /**
     * 对设备行加排他锁，使同一设备的报修提交串行化，
     * 避免并发提交时绕过「设备已存在进行中报修单」的校验。
     */
    @Select("SELECT id FROM device WHERE id = #{deviceId} FOR UPDATE")
    Long lockDeviceRow(@Param("deviceId") Long deviceId);

    /**
     * 对报修单行加排他锁，使状态机「校验 + 更新」在并发下串行化。
     */
    @Select("SELECT id FROM device_repair WHERE id = #{id} FOR UPDATE")
    Long lockRepairRow(@Param("id") Long id);
}
