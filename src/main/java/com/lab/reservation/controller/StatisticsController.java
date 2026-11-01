package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.BoardSummaryVO;
import com.lab.reservation.vo.HotLabVO;
import com.lab.reservation.vo.LabBoardVO;
import com.lab.reservation.vo.NameValueVO;
import com.lab.reservation.vo.StatsMetricsVO;
import com.lab.reservation.vo.StatsOverviewVO;
import com.lab.reservation.vo.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据统计接口（ECharts 仪表盘数据源）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "数据统计", description = "仪表盘统计与热门实验室")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "统计概览卡片")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<StatsOverviewVO> overview() {
        return Result.success(statisticsService.overview());
    }

    @Operation(summary = "热门实验室 TOP")
    @GetMapping("/hot-labs")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<List<HotLabVO>> hotLabs(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(statisticsService.hotLabs(topN));
    }

    @Operation(summary = "预约状态分布")
    @GetMapping("/reservation-status")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<List<NameValueVO>> reservationStatus() {
        return Result.success(statisticsService.reservationStatusDistribution());
    }

    @Operation(summary = "预约趋势")
    @GetMapping("/reservation-trend")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<List<TrendPointVO>> reservationTrend(
            @RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.reservationTrend(days));
    }

    @Operation(summary = "实验室类型分布")
    @GetMapping("/lab-type")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<List<NameValueVO>> labType() {
        return Result.success(statisticsService.labTypeDistribution());
    }

    @Operation(summary = "实验室实时占用看板（全员可见）")
    @GetMapping("/realtime-board")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LabBoardVO>> realtimeBoard() {
        return Result.success(statisticsService.realtimeBoard());
    }

    @Operation(summary = "实验室实时占用汇总（全员可见）")
    @GetMapping("/board-summary")
    @PreAuthorize("isAuthenticated()")
    public Result<BoardSummaryVO> boardSummary() {
        return Result.success(statisticsService.realtimeSummary());
    }

    @Operation(summary = "预约履约质量指标（通过率/驳回率/爽约率/平均审批时长）")
    @GetMapping("/quality")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<StatsMetricsVO> quality() {
        return Result.success(statisticsService.quality());
    }

    @Operation(summary = "使用率报表导出(Excel)")
    @GetMapping("/usage-export")
    @PreAuthorize("hasAuthority('statistics:view')")
    public void usageExport(
            @Parameter(hidden = true) HttpServletResponse response,
            @Parameter(description = "统计最近多少天，1-365") @RequestParam(defaultValue = "30") int days) {
        statisticsService.exportUsageExcel(response, days);
    }
}