package com.lab.reservation.vo;

import com.lab.reservation.entity.SysAnnouncement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 门户状态：维护模式 + 生效公告
 *
 * <p>前端布局在启动及定时轮询时读取，用于展示维护提示与公告横幅。
 * 该接口不含任何敏感配置。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "门户状态")
public class PortalStatusVO {

    @Schema(description = "是否维护模式")
    private Boolean maintenance;

    @Schema(description = "维护提示文案")
    private String maintenanceNotice;

    @Schema(description = "是否显示公告横幅")
    private Boolean bannerEnabled;

    @Schema(description = "生效中的公告")
    private List<SysAnnouncement> announcements;
}
