package com.lab.reservation.service;

import com.lab.reservation.vo.WeatherVO;

/**
 * 天气服务
 *
 * @author LabWise
 * @since 2.6.0
 */
public interface WeatherService {

    /**
     * 查询指定经纬度的实况天气
     *
     * <p>经纬度为空或非法时回退到默认位置（广州）。上游不可用或未配置时返回 null，
     * 由前端隐藏天气信息，不阻塞页面。</p>
     *
     * @param lat 纬度
     * @param lon 经度
     * @return 实况天气；不可用时返回 null
     */
    WeatherVO now(Double lat, Double lon);
}
