package com.lab.reservation.service;

import com.lab.reservation.vo.BoardSummaryVO;
import com.lab.reservation.vo.HotLabVO;
import com.lab.reservation.vo.LabBoardVO;
import com.lab.reservation.vo.NameValueVO;
import com.lab.reservation.vo.StatsOverviewVO;
import com.lab.reservation.vo.TrendPointVO;

import java.util.List;

/**
 * 数据统计分析服务接口
 *
 * <p style="color:#c00"><b>答辩亮点：Redis 热门数据缓存</b></p>
 * 热门实验室排行写入 Redis ZSet；每日预约概览缓存于 Redis（TTL 失效自动重算），
 * 降低高频统计查询对数据库的压力。
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface StatisticsService {

    /** 预约成功时累加热门实验室热度（供预约服务调用） */
    void recordReservationBooking(Long labId, String labName);

    /**
     * 使统计结果缓存整体失效
     *
     * <p>预约、报修等写操作后调用，保证仪表盘数据及时刷新。</p>
     */
    void evictStatCache();

    /** 热门实验室 TOP N（Redis ZSet + 兜底重建） */
    List<HotLabVO> hotLabs(int topN);

    /** 统计概览卡片数据 */
    StatsOverviewVO overview();

    /** 预约状态分布（饼图） */
    List<NameValueVO> reservationStatusDistribution();

    /** 近 7 日预约趋势（折线图） */
    List<TrendPointVO> reservationTrend(int days);

    /** 实验室类型分布（饼图） */
    List<NameValueVO> labTypeDistribution();

    /**
     * 实验室实时占用看板
     *
     * <p>按「当前时刻是否落在有效预约时段内」实时推导每间实验室的占用状态，
     * 结果写入短 TTL 的版本化缓存键，兼顾实时性与并发刷新压力。</p>
     */
    List<LabBoardVO> realtimeBoard();

    /** 实时占用汇总指标（由看板明细聚合） */
    BoardSummaryVO realtimeSummary();
}