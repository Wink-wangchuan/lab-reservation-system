package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验室周日程：一周内每天的开放时段与已占用预约
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@Schema(description = "实验室周日程")
public class WeekScheduleVO {

    private Long labId;

    private String labName;

    private LocalDate startDate;

    private LocalDate endDate;

    private List<Day> days = new ArrayList<>();

    @Data
    @Schema(description = "单日日程")
    public static class Day {

        private LocalDate date;

        @Schema(description = "星期 1-7")
        private Integer weekday;

        @Schema(description = "当日启用的开放时段")
        private List<Slot> slots = new ArrayList<>();

        @Schema(description = "当日有效预约（不含取消/驳回）")
        private List<Item> reservations = new ArrayList<>();
    }

    @Data
    @Schema(description = "开放时段")
    public static class Slot {

        private Long id;

        private String slotName;

        private LocalTime startTime;

        private LocalTime endTime;

        private Integer maxPeople;
    }

    @Data
    @Schema(description = "预约条目")
    public static class Item {

        private Long id;

        private String reservationNo;

        private String studentName;

        private LocalTime startTime;

        private LocalTime endTime;

        private Integer peopleCount;

        private String purpose;

        private String status;

        private String statusDesc;

        /** 当前登录人是否为该预约的提交人（前端可高亮本人预约） */
        private Boolean mine;
    }
}
