package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.DeviceRepairDTO;
import com.lab.reservation.service.DeviceRepairService;
import com.lab.reservation.vo.RepairDeviceOptionVO;
import com.lab.reservation.vo.RepairVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备报修接口（报修闭环）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "设备报修", description = "报修提交/处理/闭环")
@RestController
@RequestMapping("/api/repair")
@RequiredArgsConstructor
public class DeviceRepairController {

    private final DeviceRepairService repairService;

    @Operation(summary = "报修可选设备清单（精简字段，供普通用户提交报修选择）")
    @GetMapping("/device-options")
    @PreAuthorize("hasAnyAuthority('device:repair:submit', 'device:repair:handle', 'device:repair:list')")
    public Result<List<RepairDeviceOptionVO>> deviceOptions() {
        return Result.success(repairService.deviceOptions());
    }

    @Operation(summary = "提交报修（自动锁定设备与所属实验室）")
    @PostMapping
    @OperationLog(module = "设备报修", businessType = "insert", description = "提交设备报修")
    @PreAuthorize("hasAuthority('device:repair:submit')")
    public Result<RepairVO> submit(@Valid @RequestBody DeviceRepairDTO dto) {
        return Result.success("报修提交成功，设备已锁定", repairService.submit(dto));
    }

    @Operation(summary = "开始维修")
    @PostMapping("/{id}/start")
    @OperationLog(module = "设备报修", businessType = "update", description = "开始维修")
    @PreAuthorize("hasAuthority('device:repair:handle')")
    public Result<RepairVO> start(@PathVariable Long id,
                                  @RequestParam(required = false) String remark) {
        return Result.success(repairService.start(id, remark));
    }

    @Operation(summary = "完成维修（解锁设备与实验室）")
    @PostMapping("/{id}/complete")
    @OperationLog(module = "设备报修", businessType = "update", description = "完成维修")
    @PreAuthorize("hasAuthority('device:repair:handle')")
    public Result<RepairVO> complete(@PathVariable Long id,
                                     @RequestParam(required = false) String remark) {
        return Result.success("维修完成，设备已解锁", repairService.complete(id, remark));
    }

    @Operation(summary = "取消报修（解锁设备与实验室）")
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "设备报修", businessType = "update", description = "取消报修")
    @PreAuthorize("hasAuthority('device:repair:submit')")
    public Result<RepairVO> cancel(@PathVariable Long id,
                                   @RequestParam(required = false) String remark) {
        return Result.success(repairService.cancel(id, remark));
    }

    @Operation(summary = "报修单详情")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairVO> detail(@PathVariable Long id) {
        return Result.success(repairService.detail(id));
    }

    @Operation(summary = "报修单分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('device:repair:list')")
    public Result<PageVO<RepairVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        IPage<RepairVO> page = repairService.page(
                new Page<>(current, size), labId, status, keyword);
        return Result.success(PageVO.of(page, r -> r));
    }

    @Operation(summary = "我提交的报修分页")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('device:repair:submit')")
    public Result<PageVO<RepairVO>> pageMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        IPage<RepairVO> page = repairService.pageMine(
                new Page<>(current, size), status);
        return Result.success(PageVO.of(page, r -> r));
    }
}