package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.Idempotent;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.aop.RateLimit;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.RescheduleDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.service.ReservationService;
import com.lab.reservation.service.WaitlistService;
import com.lab.reservation.vo.ReservationVO;
import com.lab.reservation.vo.WaitlistVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预约管理接口
 *
 * <p style="color:#c00"><b>答辩亮点：</b>通过 {@code @PreAuthorize} 注解实现
 * RBAC 细粒度权限控制，与预约状态机/冲突校验共同构成完整业务闭环。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "预约管理", description = "预约提交/取消/审批/查询/导出")
@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final WaitlistService waitlistService;

    @Operation(summary = "提交预约（学生）")
    @PostMapping("/submit")
    @OperationLog(module = "预约管理", businessType = "insert", description = "提交实验室预约")
    @PreAuthorize("hasAuthority('reservation:submit')")
    @RateLimit(limit = 5, seconds = 60)
    @Idempotent
    public Result<ReservationVO> submit(@Valid @RequestBody ReservationSubmitDTO dto) {
        return Result.success("预约提交成功，等待审核", reservationService.submit(dto));
    }

    @Operation(summary = "预约改期（本人）")
    @PutMapping("/{id}/reschedule")
    @OperationLog(module = "预约管理", businessType = "update", description = "预约改期")
    @PreAuthorize("hasAuthority('reservation:submit')")
    @RateLimit(limit = 5, seconds = 60)
    public Result<ReservationVO> reschedule(@PathVariable Long id,
                                           @Valid @RequestBody RescheduleDTO dto) {
        return Result.success("改期成功", reservationService.reschedule(id, dto));
    }

    @Operation(summary = "取消预约（本人）")
    @PutMapping("/{id}/cancel")
    @OperationLog(module = "预约管理", businessType = "update", description = "取消预约")
    @PreAuthorize("hasAuthority('reservation:cancel')")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        reservationService.cancel(id, reason);
        return Result.success();
    }

    @Operation(summary = "审批预约（教师）")
    @PostMapping("/approve")
    @OperationLog(module = "预约管理", businessType = "update", description = "教师审批预约")
    @PreAuthorize("hasAuthority('reservation:approve')")
    public Result<ReservationVO> approve(@Valid @RequestBody ApprovalDTO dto) {
        return Result.success("审批完成", reservationService.approve(dto));
    }

    @Operation(summary = "批量审批预约（教师）")
    @PostMapping("/batch-approve")
    @OperationLog(module = "预约管理", businessType = "update", description = "批量审批预约")
    @PreAuthorize("hasAuthority('reservation:approve')")
    public Result<com.lab.reservation.vo.BatchApprovalResultVO> batchApprove(
            @Valid @RequestBody com.lab.reservation.dto.BatchApprovalDTO dto) {
        return Result.success("批量审批完成", reservationService.batchApprove(dto));
    }

    @Operation(summary = "我的预约分页")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('reservation:mine')")
    public Result<PageVO<ReservationVO>> pageMine(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "按状态过滤") @RequestParam(required = false) String status) {
        IPage<ReservationVO> page =
                reservationService.pageMyReservations(new Page<>(current, size), status);
        return Result.success(PageVO.of(page, r -> r));
    }

    @Operation(summary = "预约管理端分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('reservation:list')")
    public Result<PageVO<ReservationVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        IPage<ReservationVO> page =
                reservationService.pageAll(new Page<>(current, size), labId, status, keyword);
        return Result.success(PageVO.of(page, r -> r));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<ReservationVO> detail(@PathVariable Long id) {
        return Result.success(reservationService.getDetail(id));
    }

    @Operation(summary = "加入候补队列（时段已约满时）")
    @PostMapping("/waitlist")
    @OperationLog(module = "预约管理", businessType = "insert", description = "加入候补队列")
    @PreAuthorize("hasAuthority('reservation:submit')")
    @RateLimit(limit = 3, seconds = 60)
    public Result<WaitlistVO> joinWaitlist(@Valid @RequestBody ReservationSubmitDTO dto) {
        return Result.success("已加入候补队列", waitlistService.join(dto));
    }

    @Operation(summary = "我的候补记录")
    @GetMapping("/waitlist/mine")
    @PreAuthorize("hasAuthority('reservation:mine')")
    public Result<List<WaitlistVO>> myWaitlist() {
        return Result.success(waitlistService.mine());
    }

    @Operation(summary = "取消候补")
    @DeleteMapping("/waitlist/{id}")
    @OperationLog(module = "预约管理", businessType = "delete", description = "取消候补")
    @PreAuthorize("hasAuthority('reservation:cancel')")
    public Result<Void> cancelWaitlist(@PathVariable Long id) {
        waitlistService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "预约记录导出(Excel)")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('reservation:export')")
    public void export(
            @Parameter(hidden = true) HttpServletResponse response,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        reservationService.exportExcel(response, labId, status, keyword);
    }
}