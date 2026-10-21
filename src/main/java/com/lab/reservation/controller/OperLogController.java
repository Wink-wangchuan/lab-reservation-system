package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.service.OperLogService;
import com.lab.reservation.vo.OperLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口
 *
 * @author LabWise
 * @since 1.0.0
 */
@Tag(name = "操作日志", description = "AOP 全局操作日志查询")
@RestController
@RequestMapping("/api/oper-log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @Operation(summary = "操作日志分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:operlog:list')")
    public Result<PageVO<OperLogVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Integer status) {
        IPage<OperLogVO> page = operLogService.page(
                new Page<>(current, size), title, operName, businessType, status);
        return Result.success(PageVO.of(page, l -> l));
    }
}