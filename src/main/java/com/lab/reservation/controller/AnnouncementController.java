package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.AnnouncementDTO;
import com.lab.reservation.entity.SysAnnouncement;
import com.lab.reservation.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统公告管理接口
 *
 * @author LabWise
 * @since 4.6.0
 */
@Tag(name = "系统公告", description = "公告增删改查与上下线")
@RestController
@RequestMapping("/api/system/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "公告分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public Result<PageVO<SysAnnouncement>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        IPage<SysAnnouncement> page = announcementService.page(
                new Page<>(current, size), keyword, status);
        return Result.success(PageVO.of(page, a -> a));
    }

    @Operation(summary = "新增公告")
    @PostMapping
    @OperationLog(module = "系统公告", businessType = "insert", description = "新增系统公告")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public Result<SysAnnouncement> create(@Valid @RequestBody AnnouncementDTO dto) {
        return Result.success("已发布", announcementService.create(dto));
    }

    @Operation(summary = "编辑公告")
    @PutMapping("/{id}")
    @OperationLog(module = "系统公告", businessType = "update", description = "编辑系统公告")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public Result<SysAnnouncement> update(@PathVariable Long id,
                                          @Valid @RequestBody AnnouncementDTO dto) {
        return Result.success("已保存", announcementService.update(id, dto));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/{id}")
    @OperationLog(module = "系统公告", businessType = "delete", description = "删除系统公告")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.success("已删除", null);
    }

    @Operation(summary = "公告上线/下线")
    @PutMapping("/{id}/status")
    @OperationLog(module = "系统公告", businessType = "update", description = "切换公告状态")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        announcementService.setStatus(id, status);
        return Result.success("状态已更新", null);
    }
}
