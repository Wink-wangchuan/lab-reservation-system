package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.service.AppUpdateService;
import com.lab.reservation.vo.AppUpdateManifest;
import com.lab.reservation.vo.AppUpdateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 客户端更新接口（匿名可访问）
 *
 * <p>桌面端与 Android 端在登录前就要检查更新，因此这两个接口不要求认证。
 * 更新包由服务器托管，响应里的 {@code fallbackUrl} 供客户端在服务器下载失败时
 * 回退到 GitHub Releases。</p>
 *
 * @author LabWise
 * @since 4.5.0
 */
@Tag(name = "客户端更新", description = "版本检查与安装包下载")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppUpdateController {

    private final AppUpdateService appUpdateService;

    @Operation(summary = "检查客户端最新版本")
    @GetMapping("/update")
    public Result<AppUpdateVO> update(
            @Parameter(description = "windows / android") @RequestParam String platform) {
        String normalized = normalize(platform);
        if (normalized == null) {
            return Result.error("不支持的平台");
        }
        AppUpdateManifest.PackageInfo info = appUpdateService.latest(normalized);
        if (info == null) {
            return Result.error("暂未配置更新信息");
        }
        return Result.success(AppUpdateVO.builder()
                .platform(normalized)
                .version(info.getVersion())
                .notes(info.getNotes())
                .size(info.getSize())
                .sha256(info.getSha256())
                .downloadUrl("/api/app/download/" + normalized)
                .fallbackUrl(info.getFallbackUrl())
                .force(Boolean.TRUE.equals(info.getForce()))
                .build());
    }

    @Operation(summary = "下载客户端安装包（服务器托管）")
    @GetMapping("/download/{platform}")
    public ResponseEntity<Resource> download(@PathVariable String platform) {
        String normalized = normalize(platform);
        if (normalized == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = appUpdateService.resolveFile(normalized);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = "android".equals(normalized)
                ? MediaType.parseMediaType("application/vnd.android.package-archive")
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                .build();
        try {
            return ResponseEntity.ok()
                    .contentType(type)
                    .contentLength(Files.size(file))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .cacheControl(CacheControl.noCache())
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String normalize(String platform) {
        if (platform == null) {
            return null;
        }
        String value = platform.trim().toLowerCase(Locale.ROOT);
        return "windows".equals(value) || "android".equals(value) ? value : null;
    }
}
