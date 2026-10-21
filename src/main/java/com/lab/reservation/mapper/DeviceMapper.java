package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备台账 Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 查询某实验室下处于故障状态的设备（用于故障锁定逻辑）
     */
    @Select("SELECT * FROM device WHERE deleted = 0 AND lab_id = #{labId} AND fault_status = 1")
    List<Device> selectFaultDevicesByLabId(@Param("labId") Long labId);

    /**
     * 设备实际台数合计：台账按「设备配置」登记、每行带数量，故统计需按数量汇总而非行数
     */
    @Select("SELECT IFNULL(SUM(quantity), 0) FROM device WHERE deleted = 0")
    Long sumQuantity();
}