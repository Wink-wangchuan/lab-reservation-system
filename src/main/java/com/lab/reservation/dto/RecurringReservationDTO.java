package com.lab.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 周期预约入参：在基础预约字段上增加「连续 N 周」
 *
 * <p>按每周同一天、同一时段提交，每期独立校验/独立事务，
 * 某一周冲突不会影响其它周的成功提交。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "周期预约请求")
public class RecurringReservationDTO extends ReservationSubmitDTO {

    @Schema(description = "连续周数：1 表示仅本次，最多 8 周", example = "4")
    @NotNull(message = "重复周数不能为空")
    @Min(value = 1, message = "重复周数至少为 1")
    @Max(value = 8, message = "重复周数最多为 8")
    private Integer repeatWeeks;
}
