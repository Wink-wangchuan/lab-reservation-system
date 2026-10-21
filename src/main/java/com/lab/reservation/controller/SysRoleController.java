package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.SysRoleDTO;
import com.lab.reservation.service.SysRoleAdminService;
import com.lab.reservation.vo.SysRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统角色管理接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "角色管理", description = "角色增删改查与权限分配")
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleAdminService roleService;

    @Operation(summary = "角色分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<PageVO<SysRoleVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<SysRoleVO> page = roleService.page(new Page<>(current, size), keyword, status);
        return Result.success(PageVO.of(page, r -> r));
    }

    @Operation(summary = "全部启用角色")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<List<SysRoleVO>> listAll() {
        return Result.success(roleService.listAll());
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @OperationLog(module = "角色管理", businessType = "insert", description = "新增角色")
    @PreAuthorize("hasAuthority('system:role:add')")
    public Result<Void> add(@Valid @RequestBody SysRoleDTO dto) {
        roleService.add(dto);
        return Result.success("角色新增成功", null);
    }

    @Operation(summary = "更新角色")
    @PutMapping
    @OperationLog(module = "角色管理", businessType = "update", description = "更新角色")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public Result<Void> update(@Valid @RequestBody SysRoleDTO dto) {
        roleService.update(dto);
        return Result.success("角色更新成功", null);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @OperationLog(module = "角色管理", businessType = "delete", description = "删除角色")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success("角色删除成功", null);
    }

    @Operation(summary = "启用/禁用角色")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        roleService.toggleStatus(id, status);
        return Result.success("状态更新成功", null);
    }

    @Operation(summary = "为角色分配权限")
    @PutMapping("/{id}/perms")
    @OperationLog(module = "角色管理", businessType = "update", description = "分配权限")
    @PreAuthorize("hasAuthority('system:role:assignPerm')")
    public Result<Void> assignPerms(@PathVariable Long id, @RequestBody List<Long> permIds) {
        roleService.assignPerms(id, permIds);
        return Result.success("权限分配成功", null);
    }

    @Operation(summary = "查询角色已分配权限ID")
    @GetMapping("/{id}/perms")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<List<Long>> permsOf(@PathVariable Long id) {
        return Result.success(roleService.permsOf(id));
    }
}