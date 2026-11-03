package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量审批结果：成功条数 + 逐单失败原因
 *
 * <p>部分失败是批量操作的真实常态（单号已被别人审批、状态已变更、无权限等），
 * 这里不做「一失败就整体回滚」，而是把失败清单原样返回，让人知道哪几张没批成。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@Schema(description = "批量审批结果")
public class BatchApprovalResultVO {

    @Schema(description = "成功条数")
    private int successCount;

    @Schema(description = "失败条数")
    private int failureCount;

    @Schema(description = "失败明细")
    private List<Failure> failures = new ArrayList<>();

    /**
     * 单条失败明细
     */
    @Data
    @AllArgsConstructor
    @Schema(description = "批量审批失败明细")
    public static class Failure {

        @Schema(description = "预约单ID")
        private Long reservationId;

        @Schema(description = "失败原因")
        private String reason;
    }

    public void addFailure(Long reservationId, String reason) {
        failures.add(new Failure(reservationId, reason));
        failureCount++;
    }
}
