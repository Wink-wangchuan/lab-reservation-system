package com.lab.reservation.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 和风天气配置属性
 *
 * <p>绑定 {@code weather.*} 配置项。<b>API Key 只从服务器环境变量注入</b>
 * （systemd 单元通过 {@code EnvironmentFile} 提供），绝不写入仓库，
 * 也绝不下发到前端 —— 前端只调用本系统的 {@code /api/weather/now} 代理接口。</p>
 *
 * @author LabWise
 * @since 2.6.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

    /** 和风天气 API Host（专属域名，不含协议亦可） */
    private String host;

    /** API Key（仅来自环境变量 QWEATHER_KEY） */
    private String key;

    /** 默认纬度（广州） */
    private Double defaultLat = 23.1291;

    /** 默认经度（广州） */
    private Double defaultLon = 113.2644;

    /** 实况缓存秒数：实况 10 分钟更新一次，缓存可显著节省配额 */
    private Integer cacheSeconds = 600;

    /**
     * 是否已配置可用；key 为空即视为未配置，接口返回 null 由前端隐藏天气
     */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(host) && StrUtil.isNotBlank(key);
    }

    /** 拼接实况天气地址，location 为 经度,纬度 */
    public String nowUrl(double lat, double lon) {
        return baseUrl() + "/v7/weather/now?location=" + lon + "," + lat + "&key=" + key;
    }

    /** 拼接城市反查地址（根据经纬度解析所在城市名） */
    public String cityLookupUrl(double lat, double lon) {
        return baseUrl() + "/geo/v2/city/lookup?location=" + lon + "," + lat + "&key=" + key;
    }

    /** 去掉协议尾斜杠并补齐 https:// */
    private String baseUrl() {
        String h = StrUtil.removeSuffix(host.trim(), "/");
        return h.startsWith("http://") || h.startsWith("https://") ? h : "https://" + h;
    }
}
