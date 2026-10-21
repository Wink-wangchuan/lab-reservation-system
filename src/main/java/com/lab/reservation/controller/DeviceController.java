package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.DeviceDTO;
import com.lab.reservation.service.DeviceService;
import com.lab.reservation.vo.DeviceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备台账接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "设备台账", description = "设备增删改查")
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "设备台账分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:device:list')")
    public Result<PageVO<DeviceVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer faultStatus,
            @RequestParam(required = false) String keyword) {
        IPage<DeviceVO> page = deviceService.page(
                new Page<>(current, size), deviceType, labId, status, faultStatus, keyword);
        return Result.success(PageVO.of(page, d -> d));
    }

    @Operation(summary = "设备详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:device:list')")
    public Result<DeviceVO> detail(@PathVariable Long id) {
        return Result.success(deviceService.detail(id));
    }

    @Operation(summary = "新增设备")
    @PostMapping
    @OperationLog(module = "设备台账", businessType = "insert", description = "新增设备")
    @PreAuthorize("hasAuthority('system:device:add')")
    public Result<Void> add(@Valid @RequestBody DeviceDTO dto) {
        deviceService.add(dto);
        return Result.success("设备新增成功", null);
    }

    @Operation(summary = "更新设备")
    @PutMapping
    @OperationLog(module = "设备台账", businessType = "update", description = "更新设备")
    @PreAuthorize("hasAuthority('system:device:edit')")
    public Result<Void> update(@Valid @RequestBody DeviceDTO dto) {
        deviceService.update(dto);
        return Result.success("设备更新成功", null);
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    @OperationLog(module = "设备台账", businessType = "delete", description = "删除设备")
    @PreAuthorize("hasAuthority('system:device:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return Result.success("设备删除成功", null);
    }
}