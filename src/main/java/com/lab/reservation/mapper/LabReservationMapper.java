package com.lab.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.reservation.entity.LabReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 预约单 Mapper
 *
 * <p>提供核心的<b>时间冲突查询</b>方法，供预约业务做冲突校验。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Mapper
public interface LabReservationMapper extends BaseMapper<LabReservation> {

    /**
     * 查询指定实验室在指定日期、给定时间区间内处于"占用"状态的预约记录。
     *
     * <p style="color:#c00"><b>答辩亮点：时间冲突算法</b></p>
     * 两时间段冲突的判定条件为：现有预约的 [curStart,curEnd) 与新增 [newStart,newEnd)
     * 存在交集，即 <code>curStart &lt; newEnd AND curEnd &gt; newStart</code>。
     * bannedStatus：占位排除的预约状态（如草稿、已取消、已驳回）。
     *
     * @param labId        实验室ID
     * @param date         预约日期
     * @param newStart     新预约开始时间
     * @param newEnd       新预约结束时间
     * @param excludeId    需要排除的预约ID（编辑场景），可为 null
     * @param bannedStatus 不参与占用判定的状态集合（如草稿/取消/驳回）
     * @return 存在冲突的预约列表
     */
    List<LabReservation> selectConflictingReservations(
            @Param("labId") Long labId,
            @Param("date") LocalDate date,
            @Param("newStart") LocalTime newStart,
            @Param("newEnd") LocalTime newEnd,
            @Param("excludeId") Long excludeId,
            @Param("bannedStatus") List<String> bannedStatus);

    /**
     * 对实验室行加排他锁（SELECT ... FOR UPDATE）。
     *
     * <p style="color:#c00"><b>并发正确性：</b>预约提交是「先查冲突、后插入」的复合操作，
     * 若不加锁，同一实验室同一时段的并发请求会同时通过冲突校验并各自插入，产生重叠预约。
     * 在事务内先锁住实验室行，使同一实验室的提交串行化。</p>
     *
     * @param labId 实验室ID
     * @return 实验室ID（不存在返回 null）
     */
    @Select("SELECT id FROM lab_info WHERE id = #{labId} FOR UPDATE")
    Long lockLabRow(@Param("labId") Long labId);

    /**
     * 对预约单行加排他锁，使状态机的「校验 + 更新」在并发下串行化，
     * 避免并发审批/取消时重复流转（例如同一单被审批两次、被取消两次）。
     *
     * @param id 预约单ID
     * @return 预约单ID（不存在返回 null）
     */
    @Select("SELECT id FROM lab_reservation WHERE id = #{id} FOR UPDATE")
    Long lockReservationRow(@Param("id") Long id);
}