package com.lab.reservation.controller;

import com.lab.reservation.common.Result;
import com.lab.reservation.service.AnnouncementService;
import com.lab.reservation.service.SysConfigService;
import com.lab.reservation.vo.PortalStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户状态接口：维护模式提示 + 生效中的公告
 *
 * <p>所有登录用户都可访问，且维护模式开启时仍放行，
 * 否则用户既看不到维护提示、也无法通过前端感知服务恢复。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Tag(name = "门户状态", description = "维护模式与公告")
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final SysConfigService configService;
    private final AnnouncementService announcementService;

    @Operation(summary = "门户状态")
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public Result<PortalStatusVO> status() {
        PortalStatusVO vo = new PortalStatusVO();
        vo.setMaintenance(configService.maintenanceMode());
        vo.setMaintenanceNotice(configService.maintenanceNotice());
        vo.setBannerEnabled(configService.bannerEnabled());
        vo.setAnnouncements(announcementService.active());
        return Result.success(vo);
    }
}
