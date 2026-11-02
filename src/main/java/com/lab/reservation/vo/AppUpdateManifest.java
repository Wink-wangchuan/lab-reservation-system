package com.lab.reservation.vo;

import lombok.Data;

/**
 * 客户端更新清单（对应服务器 updates/manifest.json）
 *
 * <p>按平台区分，字段由发布脚本/运维填写；后端只负责读取与下发，
 * 不包含任何令牌或服务器内部路径。</p>
 *
 * @author LabWise
 * @since 4.5.0
 */
@Data
public class AppUpdateManifest {

    private PackageInfo windows;

    private PackageInfo android;

    public PackageInfo packageInfo(String platform) {
        if ("windows".equals(platform)) {
            return windows;
        }
        if ("android".equals(platform)) {
            return android;
        }
        return null;
    }

    /**
     * 单个平台的更新包信息
     */
    @Data
    public static class PackageInfo {

        /** 版本号，例如 4.5.0 */
        private String version;

        /** 更新说明（纯文本，客户端弹窗展示） */
        private String notes;

        /** 安装包文件名（必须位于更新目录内） */
        private String fileName;

        /** 文件字节数，0 或不填表示未知 */
        private Long size;

        /** 安装包 SHA-256（小写十六进制），空表示不校验 */
        private String sha256;

        /** GitHub Releases 兜底下载地址（服务器下载失败时使用） */
        private String fallbackUrl;

        /** 是否强制更新（预留能力，当前默认 false） */
        private Boolean force;
    }
}
