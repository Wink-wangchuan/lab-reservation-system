package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.service.WeatherService;
import com.lab.reservation.vo.WeatherVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气接口
 *
 * <p>顶栏天气展示的代理入口：和风天气的 API Key 只存在于服务端，
 * 前端永远拿不到也无需知道。</p>
 *
 * @author LabWise
 * @since 2.6.0
 */
@Tag(name = "天气", description = "当前天气查询")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "当前天气（默认广州）")
    @GetMapping("/now")
    @PreAuthorize("isAuthenticated()")
    public Result<WeatherVO> now(
            @Parameter(description = "纬度，缺省为广州") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度，缺省为广州") @RequestParam(required = false) Double lon) {
        return Result.success(weatherService.now(lat, lon));
    }
}
