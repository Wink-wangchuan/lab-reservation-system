package com.lab.reservation.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 客户端检查更新的响应
 *
 * @author LabWise
 * @since 4.5.0
 */
@Data
@Builder
public class AppUpdateVO {

    /** windows / android */
    private String platform;

    /** 最新版本号 */
    private String version;

    /** 更新说明 */
    private String notes;

    /** 安装包字节数 */
    private Long size;

    /** SHA-256（可能为空） */
    private String sha256;

    /** 服务器下载地址（相对当前 API 根路径） */
    private String downloadUrl;

    /** GitHub 兜底地址（可能为空） */
    private String fallbackUrl;

    /** 是否强制更新 */
    private Boolean force;
}
