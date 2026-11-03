package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.service.LabScheduleService;
import com.lab.reservation.vo.WeekScheduleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 实验室周日程接口
 *
 * <p>面向所有登录用户：学生查看哪些时段还空着，教师查看本周排课与占用。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Tag(name = "实验室日程", description = "按周查询实验室开放时段与预约占用")
@RestController
@RequestMapping("/api/lab/schedule")
@RequiredArgsConstructor
public class LabScheduleController {

    private final LabScheduleService labScheduleService;

    @Operation(summary = "实验室周日程")
    @GetMapping("/week")
    @PreAuthorize("isAuthenticated()")
    public Result<WeekScheduleVO> week(
            @RequestParam Long labId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return Result.success(labScheduleService.week(labId, startDate));
    }
}
