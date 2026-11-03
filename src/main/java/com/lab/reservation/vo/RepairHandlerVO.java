package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 报修可指派处理人（具备 device:repair:handle 权限的启用用户）
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@AllArgsConstructor
@Schema(description = "报修处理人")
public class RepairHandlerVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "姓名")
    private String realName;
}
