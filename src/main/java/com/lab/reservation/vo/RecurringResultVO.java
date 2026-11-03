package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 周期预约结果：逐期成功/失败明细
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "周期预约结果")
public class RecurringResultVO {

    private int successCount;

    private int failureCount;

    private List<Item> items = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @Schema(description = "单期结果")
    public static class Item {

        @Schema(description = "预约日期")
        private LocalDate date;

        @Schema(description = "是否成功")
        private Boolean success;

        @Schema(description = "失败原因")
        private String message;

        @Schema(description = "预约单ID")
        private Long reservationId;

        @Schema(description = "预约单号")
        private String reservationNo;
    }

    public void addSuccess(LocalDate date, Long id, String no) {
        items.add(new Item(date, true, null, id, no));
        successCount++;
    }

    public void addFailure(LocalDate date, String message) {
        items.add(new Item(date, false, message, null, null));
        failureCount++;
    }
}
