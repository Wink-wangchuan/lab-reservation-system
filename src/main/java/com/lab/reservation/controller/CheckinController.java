package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.CheckinVerifyDTO;
import com.lab.reservation.service.CheckinService;
import com.lab.reservation.vo.CheckinVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 预约签到核销接口
 *
 * <p style="color:#c00"><b>答辩亮点：预约业务闭环末端</b></p>
 * 审批通过 → 生成核销码 → 管理员/教师核销签到 → 签退完成。
 *
 * @author LabWise
 * @since 1.1.0
 */
@Tag(name = "签到核销", description = "核销码获取、到场签到、离场签退")
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "获取预约核销码（本人或核销人）")
    @PostMapping("/code/{reservationId}")
    @PreAuthorize("isAuthenticated()")
    public Result<CheckinVO> code(@PathVariable Long reservationId) {
        return Result.success(checkinService.codeOf(reservationId));
    }

    @Operation(summary = "核销签到（管理员/教师）")
    @PostMapping("/verify")
    @OperationLog(module = "签到核销", businessType = "update", description = "核销签到")
    @PreAuthorize("hasAuthority('checkin:verify')")
    public Result<CheckinVO> verify(@Valid @RequestBody CheckinVerifyDTO dto) {
        return Result.success("签到成功", checkinService.verify(dto));
    }

    @Operation(summary = "离场签退")
    @PostMapping("/{id}/checkout")
    @OperationLog(module = "签到核销", businessType = "update", description = "离场签退")
    @PreAuthorize("isAuthenticated()")
    public Result<CheckinVO> checkout(@PathVariable Long id,
                                      @RequestParam(required = false) String remark) {
        return Result.success("签退成功", checkinService.checkout(id, remark));
    }

    @Operation(summary = "我的签到记录分页")
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Result<PageVO<CheckinVO>> pageMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        IPage<CheckinVO> page = checkinService.pageMine(new Page<>(current, size), status);
        return Result.success(PageVO.of(page, c -> c));
    }

    @Operation(summary = "签到记录分页（管理端）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('checkin:list')")
    public Result<PageVO<CheckinVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        IPage<CheckinVO> page = checkinService.pageAll(new Page<>(current, size), status, keyword);
        return Result.success(PageVO.of(page, c -> c));
    }

    @Operation(summary = "签到单详情")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<CheckinVO> detail(@PathVariable Long id) {
        return Result.success(checkinService.detail(id));
    }
}
