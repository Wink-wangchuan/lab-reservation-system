package com.lab.reservation.controller;

import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.ConfigUpdateDTO;
import com.lab.reservation.service.SysConfigService;
import com.lab.reservation.vo.SysConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统设置接口
 *
 * <p>维护模式、公告横幅、定时任务开关、外部通知 webhook 等运行时配置。
 * SECRET 类型配置不回传明文，只返回「是否已配置」。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Tag(name = "系统设置", description = "运行时配置读写")
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @Operation(summary = "配置项列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:config:manage')")
    public Result<List<SysConfigVO>> list() {
        return Result.success(configService.listAll());
    }

    @Operation(summary = "修改配置值")
    @PutMapping
    @OperationLog(module = "系统设置", businessType = "update", description = "修改系统配置")
    @PreAuthorize("hasAuthority('system:config:manage')")
    public Result<Void> update(@Valid @RequestBody ConfigUpdateDTO dto) {
        configService.updateValue(dto.getConfigKey(), dto.getConfigValue());
        return Result.success("已保存", null);
    }
}
