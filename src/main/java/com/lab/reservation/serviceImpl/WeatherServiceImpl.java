package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lab.reservation.config.WeatherProperties;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.service.WeatherService;
import com.lab.reservation.vo.WeatherVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气服务实现
 *
 * <p>前端只感知 {@code /api/weather/now}，API Key 始终留在服务端；实况按经纬度
 * 在 Redis 缓存 10 分钟，既保证顶栏数据不会太旧，也避免刷新页面就消耗一次配额。</p>
 *
 * <p>定位策略：前端优先用浏览器定位，拿不到（拒绝授权 / 非 HTTPS 源）时回退默认
 * 位置广州；本服务再做一次经纬度合法性兜底，任何异常都只降级不抛错。</p>
 *
 * @author LabWise
 * @since 2.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    /** 天气查询是小报文短请求，连接与整体超时都必须收敛，避免拖慢顶栏 */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final WeatherProperties weatherProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public WeatherVO now(Double lat, Double lon) {
        // 未配置 Key 时直接返回 null，前端隐藏天气，不影响其余功能
        if (!weatherProperties.isConfigured()) {
            return null;
        }
        double latitude = validLat(lat) ? lat : weatherProperties.getDefaultLat();
        double longitude = validLon(lon) ? lon : weatherProperties.getDefaultLon();
        // 坐标聚合到 2 位小数（约 1km），同一区域共用一份缓存
        String location = String.format("%.2f,%.2f", longitude, latitude);
        String cacheKey = RedisKeyConstants.weatherKey(location);

        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                return JSONUtil.toBean(cached, WeatherVO.class);
            }
        } catch (Exception e) {
            // Redis 抖动不影响天气展示，直接回源
            log.debug("[天气] 读取缓存失败：{}", e.getMessage());
        }

        WeatherVO vo = fetch(latitude, longitude);
        if (vo == null) {
            return null;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo),
                    Duration.ofSeconds(Math.max(60, weatherProperties.getCacheSeconds())));
        } catch (Exception e) {
            log.debug("[天气] 写入缓存失败：{}", e.getMessage());
        }
        return vo;
    }

    /**
     * 调用和风天气实况接口
     */
    private WeatherVO fetch(double lat, double lon) {
        try {
            String body = get(weatherProperties.nowUrl(lat, lon));
            if (StrUtil.isBlank(body)) {
                return null;
            }
            JSONObject root = JSONUtil.parseObj(body);
            if (!"200".equals(root.getStr("code"))) {
                log.warn("[天气] 实况接口返回 code={}，message={}",
                        root.getStr("code"), root.getStr("message"));
                return null;
            }
            JSONObject now = root.getJSONObject("now");
            if (now == null) {
                return null;
            }
            WeatherVO vo = new WeatherVO();
            vo.setCity(resolveCity(lat, lon));
            vo.setText(now.getStr("text"));
            vo.setTemp(now.getStr("temp"));
            vo.setFeelsLike(now.getStr("feelsLike"));
            vo.setHumidity(now.getStr("humidity"));
            vo.setWindDir(now.getStr("windDir"));
            vo.setWindScale(now.getStr("windScale"));
            vo.setObsTime(now.getStr("obsTime"));
            vo.setIcon(now.getInt("icon"));
            return vo;
        } catch (Exception e) {
            log.warn("[天气] 获取实况失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 经纬度反查城市名：优先取上级城市（如「广州」），区县级名称仅作兜底
     */
    private String resolveCity(double lat, double lon) {
        try {
            String body = get(weatherProperties.cityLookupUrl(lat, lon));
            if (StrUtil.isBlank(body)) {
                return null;
            }
            JSONObject root = JSONUtil.parseObj(body);
            if (!"200".equals(root.getStr("code"))) {
                return null;
            }
            JSONArray locations = root.getJSONArray("location");
            if (locations == null || locations.isEmpty()) {
                return null;
            }
            JSONObject first = locations.getJSONObject(0);
            String city = first.getStr("adm2");
            return StrUtil.isBlank(city) ? first.getStr("name") : city;
        } catch (Exception e) {
            log.debug("[天气] 城市反查失败：{}", e.getMessage());
            return null;
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            log.warn("[天气] 上游返回 HTTP {}", response.statusCode());
            return null;
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return null;
        }
        // 和风天气边缘节点会无条件返回 gzip（即使没声明 Accept-Encoding），
        // JDK HttpClient 不会自动解压，这里按魔数判断后手动解压
        boolean gzip = body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
        if (gzip || response.headers().firstValue("content-encoding")
                .orElse("").toLowerCase().contains("gzip")) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private boolean validLat(Double value) {
        return value != null && value >= -90 && value <= 90;
    }

    private boolean validLon(Double value) {
        return value != null && value >= -180 && value <= 180;
    }
}
