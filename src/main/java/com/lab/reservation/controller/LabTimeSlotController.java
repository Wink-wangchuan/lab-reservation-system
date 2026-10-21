package com.lab.reservation.controller;

import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.LabTimeSlotDTO;
import com.lab.reservation.service.LabTimeSlotService;
import com.lab.reservation.vo.AvailableSlotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 实验室开放时段接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "实验室开放时段", description = "开放时段配置与可预约时段查询")
@RestController
@RequestMapping("/api/lab/slot")
@RequiredArgsConstructor
public class LabTimeSlotController {

    private final LabTimeSlotService slotService;

    @Operation(summary = "某实验室全部时段")
    @GetMapping("/{labId}")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LabTimeSlotDTO>> listByLab(@PathVariable Long labId) {
        return Result.success(slotService.listByLab(labId));
    }

    @Operation(summary = "批量保存开放时段")
    @PostMapping("/{labId}")
    @OperationLog(module = "开放时段配置", businessType = "update", description = "批量保存开放时段")
    @PreAuthorize("hasAuthority('system:lab:slotConfig')")
    public Result<Void> saveSlots(@PathVariable Long labId,
                                  @RequestBody List<LabTimeSlotDTO> slots) {
        slotService.saveSlots(labId, slots);
        return Result.success("开放时段配置成功", null);
    }

    @Operation(summary = "某日可预约时段")
    @GetMapping("/available/{labId}")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AvailableSlotVO>> available(
            @PathVariable Long labId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(slotService.availableSlots(labId, date));
    }
}