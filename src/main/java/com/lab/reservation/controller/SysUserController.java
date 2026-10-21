package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.SysUserDTO;
import com.lab.reservation.service.SysUserAdminService;
import com.lab.reservation.vo.SysUserVO;
import com.lab.reservation.vo.UserOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统用户管理接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "用户管理", description = "用户增删改查、角色分配、密码重置")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserAdminService userService;

    @Operation(summary = "用户分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageVO<SysUserVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer status) {
        IPage<SysUserVO> page = userService.page(
                new Page<>(current, size), keyword, roleId, status);
        return Result.success(PageVO.of(page, u -> u));
    }

    @Operation(summary = "用户下拉选项（精简字段，供选择负责人/消息接收人）")
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('system:user:list', 'system:lab:add', 'system:lab:edit', 'notice:send')")
    public Result<List<UserOptionVO>> options() {
        return Result.success(userService.options());
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.success(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @OperationLog(module = "用户管理", businessType = "insert", description = "新增用户")
    @PreAuthorize("hasAuthority('system:user:add')")
    public Result<Void> add(@Valid @RequestBody SysUserDTO dto) {
        userService.add(dto);
        return Result.success("用户新增成功（默认密码123456）", null);
    }

    @Operation(summary = "更新用户")
    @PutMapping
    @OperationLog(module = "用户管理", businessType = "update", description = "更新用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> update(@Valid @RequestBody SysUserDTO dto) {
        userService.update(dto);
        return Result.success("用户更新成功", null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-pwd")
    @OperationLog(module = "用户管理", businessType = "update", description = "重置密码")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestParam(required = false) String password) {
        userService.resetPassword(id, password);
        return Result.success("密码重置成功", null);
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    @OperationLog(module = "用户管理", businessType = "update", description = "切换用户状态")
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.toggleStatus(id, status);
        return Result.success("状态更新成功", null);
    }

    @Operation(summary = "审核通过（可同时为该账号分配角色）")
    @PutMapping("/{id}/approve")
    @OperationLog(module = "用户管理", businessType = "update", description = "审核通过注册账号并分配角色")
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) List<Long> roleIds) {
        userService.approve(id, roleIds);
        return Result.success("已通过审核并分配角色，该账号可以登录了", null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @OperationLog(module = "用户管理", businessType = "delete", description = "删除用户")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("用户删除成功", null);
    }

    @Operation(summary = "为用户分配角色")
    @PutMapping("/{id}/roles")
    @OperationLog(module = "用户管理", businessType = "update", description = "分配角色")
    @PreAuthorize("hasAuthority('system:user:assignRole')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return Result.success("角色分配成功", null);
    }
}