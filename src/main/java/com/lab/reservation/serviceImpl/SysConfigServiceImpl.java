package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.entity.SysConfig;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysConfigMapper;
import com.lab.reservation.service.SysConfigService;
import com.lab.reservation.vo.SysConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置实现：数据库持久化 + 60 秒本地缓存
 *
 * <p>维护模式会在每个请求上判断，必须避免每次请求都查库；
 * 60 秒缓存既保证开关能快速生效，也不会带来额外数据库压力。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper configMapper;

    private static final long CACHE_TTL_MS = 60_000L;

    private static final String KEY_MAINTENANCE = "system.maintenance";
    private static final String KEY_MAINTENANCE_NOTICE = "system.maintenance.notice";
    private static final String KEY_BANNER = "system.announcement.banner";

    private volatile Map<String, String> cache;
    private volatile long loadedAt;

    @Override
    public List<SysConfigVO> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .orderByAsc(SysConfig::getGroupName)
                        .orderByAsc(SysConfig::getId))
                .stream().map(this::toVo).toList();
    }

    @Override
    public void updateValue(String configKey, String configValue) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配置项不存在：" + configKey);
        }
        String normalized = StrUtil.isBlank(configValue) ? "" : configValue.trim();
        if ("BOOLEAN".equalsIgnoreCase(config.getValueType())
                && !normalized.isEmpty()
                && !"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "布尔配置只能是 true 或 false");
        }
        config.setConfigValue(normalized);
        configMapper.updateById(config);
        invalidate();
    }

    @Override
    public String get(String key, String defaultValue) {
        String value = values().get(key);
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = values().get(key);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    @Override
    public boolean maintenanceMode() {
        return getBoolean(KEY_MAINTENANCE, false);
    }

    @Override
    public String maintenanceNotice() {
        return get(KEY_MAINTENANCE_NOTICE, "系统维护中，请稍后再试。");
    }

    @Override
    public boolean bannerEnabled() {
        return getBoolean(KEY_BANNER, true);
    }

    @Override
    public void invalidate() {
        loadedAt = 0L;
        cache = null;
    }

    private Map<String, String> values() {
        long now = System.currentTimeMillis();
        if (cache == null || now - loadedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || System.currentTimeMillis() - loadedAt > CACHE_TTL_MS) {
                    cache = configMapper.selectList(new LambdaQueryWrapper<SysConfig>())
                            .stream()
                            .filter(c -> c.getConfigKey() != null)
                            .collect(Collectors.toMap(SysConfig::getConfigKey,
                                    c -> c.getConfigValue() == null ? "" : c.getConfigValue(),
                                    (a, b) -> b));
                    loadedAt = System.currentTimeMillis();
                }
            }
        }
        return cache;
    }

    private SysConfigVO toVo(SysConfig config) {
        SysConfigVO vo = new SysConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setValueType(config.getValueType());
        vo.setGroupName(config.getGroupName());
        vo.setLabel(config.getLabel());
        vo.setDescription(config.getDescription());
        vo.setUpdateTime(config.getUpdateTime());
        if ("SECRET".equalsIgnoreCase(config.getValueType())) {
            // 密钥类配置只暴露「是否已配置」，真实值只能通过覆盖写入更新
            vo.setConfigured(StrUtil.isNotBlank(config.getConfigValue()));
            vo.setConfigValue("");
        } else {
            vo.setConfigured(true);
            vo.setConfigValue(config.getConfigValue());
        }
        return vo;
    }
}
