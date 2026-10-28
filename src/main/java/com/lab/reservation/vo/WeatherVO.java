package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前天气 VO（和风天气实况的精简投影）
 *
 * <p>只回传顶栏展示需要的字段，不透传上游完整报文，避免把用不到的
 * 观测数据（气压、能见度等）暴露给前端。</p>
 *
 * @author LabWise
 * @since 2.6.0
 */
@Data
@Schema(description = "当前天气")
public class WeatherVO {

    @Schema(description = "城市名（经纬度反查，默认广州）")
    private String city;

    @Schema(description = "天气现象文字，如「多云」")
    private String text;

    @Schema(description = "温度（摄氏度）")
    private String temp;

    @Schema(description = "体感温度（摄氏度）")
    private String feelsLike;

    @Schema(description = "和风天气图标编码，如 100 晴 / 101 多云")
    private Integer icon;

    @Schema(description = "相对湿度（百分比）")
    private String humidity;

    @Schema(description = "风向，如「东南风」")
    private String windDir;

    @Schema(description = "风力等级")
    private String windScale;

    @Schema(description = "观测时间")
    private String obsTime;
}
