package com.lab.reservation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 客户端更新配置
 *
 * <p>更新包与版本清单由服务器托管，默认放在后端工作目录下的 {@code updates/}，
 * 通过 {@code APP_UPDATE_DIR} / {@code APP_UPDATE_MANIFEST} 环境变量覆盖。</p>
 *
 * @author LabWise
 * @since 4.5.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.update")
public class AppUpdateProperties {

    /** 安装包与清单所在目录（相对后端工作目录） */
    private String dir = "updates";

    /** 版本清单文件名 */
    private String manifest = "manifest.json";

    public Path dirPath() {
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    public Path manifestPath() {
        return dirPath().resolve(manifest).normalize();
    }
}
