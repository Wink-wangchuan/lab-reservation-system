package com.lab.reservation.service;

import com.lab.reservation.vo.SysConfigVO;

import java.util.List;

/**
 * 系统配置服务
 *
 * @author LabWise
 * @since 4.6.0
 */
public interface SysConfigService {

    /** 配置项列表（SECRET 类型只返回是否已配置） */
    List<SysConfigVO> listAll();

    /** 修改配置值；空白值表示清空 */
    void updateValue(String configKey, String configValue);

    /** 读取字符串配置 */
    String get(String key, String defaultValue);

    /** 读取布尔配置 */
    boolean getBoolean(String key, boolean defaultValue);

    /** 维护模式是否开启 */
    boolean maintenanceMode();

    /** 维护提示文案 */
    String maintenanceNotice();

    /** 是否展示公告横幅 */
    boolean bannerEnabled();

    /** 立即失效本地缓存（配置修改后调用） */
    void invalidate();
}
