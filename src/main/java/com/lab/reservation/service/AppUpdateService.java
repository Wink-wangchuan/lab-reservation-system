package com.lab.reservation.service;

import com.lab.reservation.vo.AppUpdateManifest;

import java.nio.file.Path;

/**
 * 客户端更新服务
 *
 * @author LabWise
 * @since 4.5.0
 */
public interface AppUpdateService {

    /**
     * 读取指定平台的最新版本信息
     *
     * @param platform windows / android
     * @return 未配置或清单不可读时返回 null
     */
    AppUpdateManifest.PackageInfo latest(String platform);

    /**
     * 解析安装包文件；文件不存在或不在更新目录内时返回 null
     *
     * @param platform windows / android
     */
    Path resolveFile(String platform);
}
