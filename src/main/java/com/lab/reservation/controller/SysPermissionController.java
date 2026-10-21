package com.lab.reservation.controller;

import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.SysPermissionDTO;
import com.lab.reservation.service.SysPermissionService;
import com.lab.reservation.vo.PermissionNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限（菜单/按钮）管理接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "权限管理", description = "权限树与增删改查")
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    @Operation(summary = "权限树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<List<PermissionNodeVO>> tree() {
        return Result.success(permissionService.tree());
    }

    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PermissionNodeVO> detail(@PathVariable Long id) {
        return Result.success(permissionService.detail(id));
    }

    @Operation(summary = "新增权限")
    @PostMapping
    @OperationLog(module = "权限管理", businessType = "insert", description = "新增权限")
    @PreAuthorize("hasAuthority('system:permission:add')")
    public Result<Void> add(@Valid @RequestBody SysPermissionDTO dto) {
        permissionService.add(dto);
        return Result.success("权限新增成功", null);
    }

    @Operation(summary = "更新权限")
    @PutMapping
    @OperationLog(module = "权限管理", businessType = "update", description = "更新权限")
    @PreAuthorize("hasAuthority('system:permission:edit')")
    public Result<Void> update(@Valid @RequestBody SysPermissionDTO dto) {
        permissionService.update(dto);
        return Result.success("权限更新成功", null);
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    @OperationLog(module = "权限管理", businessType = "delete", description = "删除权限")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success("权限删除成功", null);
    }
}