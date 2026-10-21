package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志 VO
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "操作日志")
public class OperLogVO {

    private Long id;

    @Schema(description = "模块标题")
    private String title;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "请求方法全限定名")
    private String method;

    @Schema(description = "HTTP方法")
    private String requestMethod;

    @Schema(description = "操作人")
    private String operName;

    @Schema(description = "操作IP")
    private String operIp;

    @Schema(description = "请求URL")
    private String operUrl;

    @Schema(description = "请求参数")
    private String operParam;

    @Schema(description = "返回结果")
    private String jsonResult;

    @Schema(description = "异常信息")
    private String errorMsg;

    @Schema(description = "耗时(ms)")
    private Long costTime;

    @Schema(description = "状态：0失败 1成功")
    private Integer status;

    @Schema(description = "操作时间")
    private LocalDateTime operTime;
}