package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.AiActionService;
import com.lab.reservation.service.LabTimeSlotService;
import com.lab.reservation.service.ReservationService;
import com.lab.reservation.vo.AvailableSlotVO;
import com.lab.reservation.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 代办操作实现：把自然语言指令解析成真实业务调用
 *
 * <p>设计取舍：<b>不依赖大模型的 Function Calling</b>，而是用规则识别「祈使句 + 动作词 +
 * 业务参数」，直接调用既有 Service。这样做有三个好处：</p>
 * <ol>
 *   <li>执行结果确定，不会因为模型幻觉而提交错误数据；</li>
 *   <li>所有权限与业务校验仍然走原服务（冲突检测、故障锁定、不能自审等），不会绕过规则；</li>
 *   <li>识别成功时不再调用上游模型，省 token 且响应更快。</li>
 * </ol>
 *
 * @author LabWise
 * @since 3.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiActionServiceImpl implements AiActionService {

    /** 单次最多审批条数：避免一句话把大批待办全部处理掉 */
    private static final int MAX_APPROVE_BATCH = 20;

    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern WEEK_DAY = Pattern.compile("(周|星期|礼拜)([一二三四五六日天])");
    private static final Pattern CLOCK = Pattern.compile("(上午|下午|晚上)?\\s*(\\d{1,2})[:：点](\\d{1,2})?");

    private final ReservationService reservationService;
    private final LabTimeSlotService labTimeSlotService;
    private final LabInfoMapper labInfoMapper;

    @Override
    public Result handle(String question) {
        if (StrUtil.isBlank(question)) {
            return new Result(false, null);
        }
        String q = question.trim();
        if (!looksLikeAction(q)) {
            return new Result(false, null);
        }
        if (containsAny(q, "审批", "审核", "批准", "驳回", "拒绝", "通过")) {
            return new Result(true, handleApproval(q));
        }
        if (containsAny(q, "预约", "约实验室", "申请")) {
            return new Result(true, handleBooking(q));
        }
        return new Result(false, null);
    }

    /* ==================== 意图识别 ==================== */

    /**
     * 判断是否为「操作指令」而非「业务咨询」
     *
     * <p>必须同时满足：有祈使语气（帮我/我想…）且不含问询词（规则/怎么/是否…），
     * 否则「帮我看看预约规则」会被误当成下单。</p>
     */
    private boolean looksLikeAction(String q) {
        boolean imperative = containsAny(q, "帮我", "替我", "给我", "请帮", "我要", "我想", "我想要", "麻烦");
        if (!imperative) {
            return false;
        }
        boolean questionLike = containsAny(q, "规则", "流程", "怎么", "如何", "是什么", "哪些", "为什么",
                "能不能", "可以吗", "是否", "吗？", "吗?", "状态", "进度", "查询", "查看", "看看", "查一下",
                "记录", "列表", "多少", "有没有");
        return !questionLike;
    }

    /* ==================== 预约申请 ==================== */

    private String handleBooking(String question) {
        if (!SecurityUtil.hasPermission("reservation:submit")) {
            return "❌ 当前账号没有预约提交权限，无法代你提交申请。";
        }
        LabInfo lab = matchLab(question);
        if (lab == null) {
            return "没找到对应的实验室。请把完整名称告诉我，例如「帮我预约本周五 第一计算机实验室」。";
        }
        LocalDate date = parseDate(question);
        if (date == null) {
            return "请告诉我要预约哪一天，例如「本周五」「明天」或「2026-10-30」。";
        }
        if (date.isBefore(LocalDate.now())) {
            return "预约日期不能早于今天（" + LocalDate.now() + "），请换一个日期。";
        }

        List<AvailableSlotVO> slots = labTimeSlotService.availableSlots(lab.getId(), date).stream()
                .filter(slot -> !Boolean.TRUE.equals(slot.getOccupied()))
                .sorted(Comparator.comparing(AvailableSlotVO::getStartTime))
                .toList();
        if (slots.isEmpty()) {
            return "「" + lab.getLabName() + "」在 " + formatDate(date) + " 没有可预约时段，"
                    + "可能当天未开放或已被约满。可以换个日期或实验室再试。";
        }

        AvailableSlotVO slot = pickSlot(question, slots);
        ReservationSubmitDTO dto = new ReservationSubmitDTO();
        dto.setLabId(lab.getId());
        dto.setReserveDate(date);
        dto.setStartTime(slot.getStartTime());
        dto.setEndTime(slot.getEndTime());
        dto.setPeopleCount(1);
        dto.setPurpose("AI 助手代办预约");

        try {
            ReservationVO vo = reservationService.submit(dto);
            StringBuilder sb = new StringBuilder();
            sb.append("✅ 已为你提交预约申请，等待审批\n\n");
            sb.append("- 预约单号：").append(vo.getReservationNo()).append('\n');
            sb.append("- 实验室：").append(vo.getLabName()).append('\n');
            sb.append("- 日期：").append(formatDate(vo.getReserveDate())).append('\n');
            sb.append("- 时段：").append(hhmm(vo.getStartTime())).append(" - ").append(hhmm(vo.getEndTime())).append('\n');
            sb.append("- 人数：1\n");
            sb.append("- 状态：").append(vo.getStatusDesc()).append("\n\n");
            sb.append("审批通过后会生成 6 位核销码，可在「我的签到」查看；如需更换时段，先取消这条预约再重新提交。");
            return sb.toString();
        } catch (BusinessException e) {
            log.info("[AI 代办] 预约提交被业务规则拒绝：{}", e.getMessage());
            return "❌ 预约没有提交成功：" + e.getMessage() + "\n\n可以换个时段/日期，或先到「实时占用看板」确认空闲情况。";
        } catch (Exception e) {
            log.warn("[AI 代办] 预约提交异常", e);
            return "❌ 预约提交时出现异常，请稍后再试或直接在「预约申请」页面提交。";
        }
    }

    /**
     * 在前端可预约时段里挑一个：优先按用户给出的时间匹配，其次按时段名称/上下午，最后取最早时段
     */
    private AvailableSlotVO pickSlot(String question, List<AvailableSlotVO> slots) {
        LocalTime wanted = parseTime(question);
        if (wanted != null) {
            AvailableSlotVO same = slots.stream()
                    .filter(s -> s.getStartTime().equals(wanted))
                    .findFirst().orElse(null);
            if (same != null) {
                return same;
            }
            // 允许用户口述的时间与整点时段有偏差，取最接近的一个
            return slots.stream()
                    .min(Comparator.comparingLong(s ->
                            Math.abs(Duration.between(s.getStartTime(), wanted).toMinutes())))
                    .orElse(slots.get(0));
        }
        for (AvailableSlotVO slot : slots) {
            if (StrUtil.isNotBlank(slot.getSlotName()) && question.contains(slot.getSlotName())) {
                return slot;
            }
        }
        if (question.contains("上午")) {
            return slots.stream().filter(s -> s.getStartTime().getHour() < 12).findFirst().orElse(slots.get(0));
        }
        if (question.contains("下午") || question.contains("晚上")) {
            return slots.stream().filter(s -> s.getStartTime().getHour() >= 12).findFirst().orElse(slots.get(0));
        }
        return slots.get(0);
    }

    /* ==================== 批量审批 ==================== */

    private String handleApproval(String question) {
        if (!SecurityUtil.hasPermission("reservation:approve")) {
            return "❌ 当前账号没有预约审批权限，无法代你审批。";
        }
        boolean reject = containsAny(question, "驳回", "拒绝");
        String action = reject ? "REJECT" : "APPROVE";
        String comment = reject ? "AI 助手根据指令驳回" : "AI 助手根据指令通过";

        IPage<ReservationVO> page = reservationService.pageAll(new Page<>(1, 200), null, "PENDING", null);
        Long currentUserId = SecurityUtil.getUserId();
        List<ReservationVO> candidates = page.getRecords().stream()
                .filter(r -> !Objects.equals(r.getStudentId(), currentUserId))
                .toList();
        if (candidates.isEmpty()) {
            return "当前没有可审批的待审核预约（自己的单据不能自审）。";
        }

        List<ReservationVO> batch = candidates.size() > MAX_APPROVE_BATCH
                ? candidates.subList(0, MAX_APPROVE_BATCH)
                : candidates;
        int success = 0;
        int skipped = 0;
        List<String> lines = new ArrayList<>();
        for (ReservationVO r : batch) {
            ApprovalDTO dto = new ApprovalDTO();
            dto.setReservationId(r.getId());
            dto.setAction(action);
            dto.setComment(comment);
            try {
                reservationService.approve(dto);
                success++;
                lines.add("- " + r.getReservationNo() + " " + r.getLabName() + " "
                        + formatDate(r.getReserveDate()) + " ——— " + (reject ? "已驳回" : "已通过"));
            } catch (BusinessException e) {
                skipped++;
                lines.add("- " + r.getReservationNo() + " " + r.getLabName() + " ——— 跳过：" + e.getMessage());
            } catch (Exception e) {
                skipped++;
                log.warn("[AI 代办] 审批异常 id={}", r.getId(), e);
                lines.add("- " + r.getReservationNo() + " ——— 跳过：处理异常");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(reject ? "✅ 批量驳回完成" : "✅ 批量审批完成").append('\n');
        sb.append("成功 ").append(success).append(" 条，跳过 ").append(skipped).append(" 条");
        if (candidates.size() > MAX_APPROVE_BATCH) {
            sb.append("（单次最多处理 ").append(MAX_APPROVE_BATCH)
                    .append(" 条，还剩 ").append(candidates.size() - MAX_APPROVE_BATCH).append(" 条待处理）");
        }
        sb.append("\n\n").append(String.join("\n", lines));
        return sb.toString();
    }

    /* ==================== 解析辅助 ==================== */

    private LabInfo matchLab(String question) {
        List<LabInfo> labs = labInfoMapper.selectList(null);
        LabInfo best = null;
        int bestLen = 0;
        for (LabInfo lab : labs) {
            String name = lab.getLabName();
            if (StrUtil.isBlank(name)) {
                continue;
            }
            String shortName = name.replace("实验室", "");
            boolean hit = question.contains(name) || (shortName.length() >= 2 && question.contains(shortName));
            if (hit && name.length() > bestLen) {
                best = lab;
                bestLen = name.length();
            }
        }
        return best;
    }

    /**
     * 从自然语言里解析预约日期（供单测直接调用）
     */
    static LocalDate parseDate(String question) {
        LocalDate today = LocalDate.now();

        Matcher iso = ISO_DATE.matcher(question);
        if (iso.find()) {
            return safeDate(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3)));
        }
        if (question.contains("大后天")) {
            return today.plusDays(3);
        }
        if (question.contains("后天")) {
            return today.plusDays(2);
        }
        if (question.contains("今天") || question.contains("今日")) {
            return today;
        }
        if (question.contains("明天") || question.contains("明日")) {
            return today.plusDays(1);
        }

        Matcher clock = MONTH_DAY.matcher(question);
        if (clock.find()) {
            LocalDate date = safeDate(today.getYear(), Integer.parseInt(clock.group(1)),
                    Integer.parseInt(clock.group(2)));
            if (date != null && date.isBefore(today)) {
                date = safeDate(today.getYear() + 1, Integer.parseInt(clock.group(1)),
                        Integer.parseInt(clock.group(2)));
            }
            return date;
        }

        DayOfWeek dayOfWeek = parseWeekDay(question);
        if (dayOfWeek != null) {
            boolean nextWeek = question.contains("下周") || question.contains("下个星期");
            boolean thisWeek = question.contains("本周") || question.contains("这周") || question.contains("这个星期");
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate target = monday.plusDays(dayOfWeek.getValue() - 1L);
            if (nextWeek) {
                target = target.plusWeeks(1);
            } else if (!thisWeek && target.isBefore(today)) {
                // 只说「周五」且本周五已过，理解为下一个周五
                target = target.plusWeeks(1);
            }
            return target;
        }
        return null;
    }

    private static DayOfWeek parseWeekDay(String question) {
        Matcher m = WEEK_DAY.matcher(question);
        if (!m.find()) {
            return null;
        }
        return switch (m.group(2)) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析用户口述的时间，如「9点」「08:00」「下午2点」
     */
    private static LocalTime parseTime(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        Matcher m = CLOCK.matcher(question);
        if (!m.find()) {
            return null;
        }
        int hour = Integer.parseInt(m.group(2));
        int minute = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        String period = m.group(1);
        if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
            hour += 12;
        }
        if (hour > 23 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private static String formatDate(LocalDate date) {
        return date + "（" + WEEK_NAMES[date.getDayOfWeek().getValue() - 1] + "）";
    }

    private static String hhmm(LocalTime time) {
        return time == null ? "" : time.toString().substring(0, 5);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
