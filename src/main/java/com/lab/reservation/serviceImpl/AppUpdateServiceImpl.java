package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.reservation.config.AppUpdateProperties;
import com.lab.reservation.service.AppUpdateService;
import com.lab.reservation.vo.AppUpdateManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * 客户端更新服务实现
 *
 * <p>清单文件由运维在发版时更新，读取时按文件修改时间缓存，避免每次请求都解析 JSON。
 * 下载路径只允许清单里声明的文件名，并做目录穿越校验。</p>
 *
 * @author LabWise
 * @since 4.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUpdateServiceImpl implements AppUpdateService {

    private final AppUpdateProperties properties;
    private final ObjectMapper objectMapper;

    private volatile AppUpdateManifest cached;
    private volatile long cachedModified = Long.MIN_VALUE;

    @Override
    public AppUpdateManifest.PackageInfo latest(String platform) {
        AppUpdateManifest manifest = load();
        if (manifest == null) {
            return null;
        }
        AppUpdateManifest.PackageInfo info = manifest.packageInfo(platform);
        if (info == null || StrUtil.isBlank(info.getVersion()) || StrUtil.isBlank(info.getFileName())) {
            return null;
        }
        return info;
    }

    @Override
    public Path resolveFile(String platform) {
        AppUpdateManifest.PackageInfo info = latest(platform);
        if (info == null) {
            return null;
        }
        Path dir = properties.dirPath();
        Path file = dir.resolve(info.getFileName()).normalize();
        // 清单同样可能被误写，必须确保文件仍在更新目录内
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            log.warn("[应用更新] 安装包不存在或路径非法：{}", file);
            return null;
        }
        return file;
    }

    /**
     * 按修改时间缓存清单；文件缺失或格式错误时只记日志并返回 null，不影响主站
     */
    private AppUpdateManifest load() {
        Path path = properties.manifestPath();
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            FileTime modified = Files.getLastModifiedTime(path);
            long millis = modified.toMillis();
            if (cached != null && millis == cachedModified) {
                return cached;
            }
            synchronized (this) {
                modified = Files.getLastModifiedTime(path);
                if (cached != null && modified.toMillis() == cachedModified) {
                    return cached;
                }
                cached = objectMapper.readValue(path.toFile(), AppUpdateManifest.class);
                cachedModified = modified.toMillis();
                log.info("[应用更新] 已加载版本清单：{}", path);
                return cached;
            }
        } catch (Exception e) {
            log.warn("[应用更新] 读取版本清单失败：{}", e.getMessage());
            return null;
        }
    }
}
