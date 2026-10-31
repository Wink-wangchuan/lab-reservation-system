package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.LabInfoDTO;
import com.lab.reservation.service.LabInfoService;
import com.lab.reservation.vo.LabInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实验室管理接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "实验室管理", description = "实验室增删改查与开关控制")
@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabInfoController {

    private final LabInfoService labInfoService;

    @Operation(summary = "实验室分页")
    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public Result<PageVO<LabInfoVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String labType,
            @RequestParam(required = false) String labName,
            @RequestParam(required = false) Integer openStatus,
            @RequestParam(required = false) String keyword) {
        IPage<LabInfoVO> page = labInfoService.page(
                new Page<>(current, size), labType, labName, openStatus, keyword);
        return Result.success(PageVO.of(page, l -> l));
    }

    @Operation(summary = "全部在用实验室（下拉）")
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public Result<List<LabInfoVO>> listAll() {
        return Result.success(labInfoService.listAll());
    }

    @Operation(summary = "实验室详情")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<LabInfoVO> detail(@PathVariable Long id) {
        return Result.success(labInfoService.detail(id));
    }

    @Operation(summary = "新增实验室")
    @PostMapping
    @OperationLog(module = "实验室管理", businessType = "insert", description = "新增实验室")
    @PreAuthorize("hasAuthority('system:lab:add')")
    public Result<Void> add(@Valid @RequestBody LabInfoDTO dto) {
        labInfoService.add(dto);
        return Result.success("实验室新增成功", null);
    }

    @Operation(summary = "更新实验室")
    @PutMapping
    @OperationLog(module = "实验室管理", businessType = "update", description = "更新实验室")
    @PreAuthorize("hasAuthority('system:lab:edit')")
    public Result<Void> update(@Valid @RequestBody LabInfoDTO dto) {
        labInfoService.update(dto);
        return Result.success("实验室更新成功", null);
    }

    @Operation(summary = "删除实验室")
    @DeleteMapping("/{id}")
    @OperationLog(module = "实验室管理", businessType = "delete", description = "删除实验室")
    @PreAuthorize("hasAuthority('system:lab:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        labInfoService.delete(id);
        return Result.success("实验室删除成功", null);
    }

    @Operation(summary = "开放/关闭预约开关")
    @PutMapping("/{id}/open")
    @PreAuthorize("hasAuthority('system:lab:edit')")
    public Result<Void> toggleOpen(@PathVariable Long id, @RequestParam Integer openStatus) {
        labInfoService.toggleOpen(id, openStatus);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "启用/禁止预约（管控）")
    @PutMapping("/{id}/reserve")
    @PreAuthorize("hasAuthority('system:lab:edit')")
    public Result<Void> toggleReserve(@PathVariable Long id, @RequestParam Integer enableReserve) {
        labInfoService.toggleReserve(id, enableReserve);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "切换时段占用模式（可共享/整间独占）")
    @PutMapping("/{id}/share-mode")
    @OperationLog(module = "实验室管理", businessType = "update", description = "切换时段占用模式")
    @PreAuthorize("hasAuthority('system:lab:edit')")
    public Result<Void> toggleShareMode(@PathVariable Long id, @RequestParam Integer shareMode) {
        labInfoService.toggleShareMode(id, shareMode);
        return Result.success("操作成功", null);
    }
}