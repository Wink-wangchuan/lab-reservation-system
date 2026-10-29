package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.dto.AiActionPlan;
import com.lab.reservation.dto.ApprovalDTO;
import com.lab.reservation.dto.DeviceRepairDTO;
import com.lab.reservation.dto.ReservationSubmitDTO;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.AiActionService;
import com.lab.reservation.service.DeviceRepairService;
import com.lab.reservation.service.LabTimeSlotService;
import com.lab.reservation.service.ReservationService;
import com.lab.reservation.vo.AvailableSlotVO;
import com.lab.reservation.vo.RepairDeviceOptionVO;
import com.lab.reservation.vo.RepairVO;
import com.lab.reservation.vo.ReservationVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 代办操作实现：把自然语言指令折算成真实业务调用
 *
 * <p>与 v3.0 的规则硬匹配相比，本版做了三件事让它「不那么死板」：</p>
 * <ol>
 *   <li><b>语义理解交给大模型</b>：{@link AiIntentParser} 把「一机房」「明天上午」「把张三那单批了」
 *       解析成结构化参数；模型不可用时自动回退规则解析，功能不中断。</li>
 *   <li><b>缺参数会反问而不是报错</b>：实验室匹配到多间、日期没听懂时，先把候选列出来追问一句，
 *       用户回「第二个」或「第二计算机」即可继续，中间状态放 Redis 三分钟。</li>
 *   <li><b>指定日期约满会顺延</b>：用户要的那天没有可约时段时，自动在之后 7 天里找最近可约时段并提交，
 *       同时在结果里说明改到了哪天，避免「一句约不上就结束」。</li>
 * </ol>
 *
 * <p>所有执行仍然走原业务服务：权限、时段冲突、实验室关闭、设备故障锁定、不可自审等规则
 * 一条都不会被绕过。</p>
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

    /** 指定日期无空位时，向后寻找的天数 */
    private static final int FALLBACK_DAYS = 7;

    /** 澄清时最多列出的候选数量 */
    private static final int MAX_OPTIONS = 6;

    /** 澄清上下文有效期 */
    private static final Duration PENDING_TTL = Duration.ofMinutes(3);

    private static final String TYPE_LAB = "LAB";
    private static final String TYPE_DATE = "DATE";
    private static final String TYPE_RESERVATION = "RESERVATION";
    private static final String TYPE_DEVICE = "DEVICE";

    private static final String[] WEEK_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern WEEK_DAY = Pattern.compile("(周|星期|礼拜)([一二三四五六日天])");
    private static final Pattern CLOCK = Pattern.compile(
            "(上午|下午|晚上|中午)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*[:：点时]\\s*"
                    + "(\\d{1,2}|[一二两三四五六七八九十]{1,3})?\\s*(半)?");
    private static final Pattern SECTION = Pattern.compile("第([一二三四])(?:节|节课)");
    private static final Pattern ORDINAL = Pattern.compile("第?([一二三四五六七八九十]|\\d{1,2})[个条间]?");
    private static final Pattern NAME_SUFFIX = Pattern.compile("(实验室|实训室|训练室|训练中心|机房|中心|室)$");

    /** 第 X 节课对应的开始时间（用于「第二节的课」这类表达） */
    private static final Map<String, LocalTime> SECTION_START = Map.of(
            "一", LocalTime.of(8, 0),
            "二", LocalTime.of(10, 0),
            "三", LocalTime.of(14, 0),
            "四", LocalTime.of(16, 0));

    private final ReservationService reservationService;
    private final LabTimeSlotService labTimeSlotService;
    private final LabInfoMapper labInfoMapper;
    private final DeviceRepairService deviceRepairService;
    private final AiIntentParser intentParser;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result handle(String question) {
        if (StrUtil.isBlank(question)) {
            return new Result(false, null);
        }
        String q = question.trim();
        Long userId = SecurityUtil.getUserId();

        // 1. 上一轮留下了待澄清的问题：先尝试把这句话当成答案
        Pending pending = readPending(userId);
        if (pending != null) {
            String applied = applyPending(pending, q, userId);
            if (applied != null) {
                clearPending(userId);
                return new Result(true, applied);
            }
            if (isShortAnswer(q)) {
                // 既没对上候选、又不像新指令：再追问一次，而不是抛一堆看不明白的结果
                return new Result(true, pending.getPrompt());
            }
            clearPending(userId);
        }

        // 2. 普通咨询直接交回问答流程，避免每句话都多花一次模型解析
        if (!intentParser.looksOperational(q)) {
            return new Result(false, null);
        }
        // 3. 本地解析动作与参数（零延迟），执行时仍走原业务服务的全部校验
        AiActionPlan plan = intentParser.parse(q);
        if (plan == null || AiActionPlan.NONE.equals(plan.getAction())) {
            return new Result(false, null);
        }
        return new Result(true, execute(plan, q, userId));
    }

    private String execute(AiActionPlan plan, String question, Long userId) {
        return switch (plan.getAction()) {
            case AiActionPlan.BOOK -> book(plan, question, userId);
            case AiActionPlan.CANCEL -> cancel(plan, question, userId);
            case AiActionPlan.APPROVE -> approve(plan, question, userId);
            case AiActionPlan.REPAIR -> repair(plan, question, userId);
            default -> "";
        };
    }

    /* ==================== 预约 ==================== */

    private String book(AiActionPlan plan, String question, Long userId) {
        if (!SecurityUtil.hasPermission("reservation:submit")) {
            return "❌ 当前账号没有预约提交权限，无法代你提交申请。";
        }
        LabResolve resolved = resolveLab(plan, question, userId);
        if (resolved.lab() == null) {
            return resolved.clarify();
        }
        LabInfo lab = resolved.lab();

        LocalDate wantedDate = parseDate(StrUtil.isNotBlank(plan.getDateText()) ? plan.getDateText() : question);
        if (wantedDate == null) {
            String prompt = "你要约哪一天？说「本周五」「明天」「下周一」或「2026-10-30」都可以。";
            savePending(userId, new Pending(TYPE_DATE, question, prompt, plan, null));
            return prompt;
        }
        if (wantedDate.isBefore(LocalDate.now())) {
            return "你给的日期 " + formatDate(wantedDate) + " 已经过去了，说个今天或之后的日期吧。";
        }

        LocalTime wanted = parseTime(StrUtil.isNotBlank(plan.getTimeText()) ? plan.getTimeText() : question);
        String preference = preference(plan.getTimeText(), question);
        SlotPick pick = pickWithFallback(lab, wantedDate, wanted, preference);
        if (pick == null) {
            return "「" + lab.getLabName() + "」从 " + formatDate(wantedDate) + " 起的 " + FALLBACK_DAYS
                    + " 天内都没有可预约时段（未开放或已约满）。换个实验室或日期再试试吧。";
        }

        int people = plan.getPeopleCount() == null ? 1 : plan.getPeopleCount();
        if (people > lab.getCapacity()) {
            return "「" + lab.getLabName() + "」最多容纳 " + lab.getCapacity() + " 人，你说的 " + people
                    + " 人超了，告诉我人数或者换一间容量更大的实验室？";
        }

        ReservationSubmitDTO dto = new ReservationSubmitDTO();
        dto.setLabId(lab.getId());
        dto.setReserveDate(pick.date());
        dto.setStartTime(pick.slot().getStartTime());
        dto.setEndTime(pick.slot().getEndTime());
        dto.setPeopleCount(people);
        dto.setPurpose(StrUtil.isNotBlank(plan.getPurpose()) ? plan.getPurpose() : "AI 助手代办预约");

        try {
            ReservationVO vo = reservationService.submit(dto);
            return bookReport(vo, lab, pick, wantedDate, wanted, preference);
        } catch (BusinessException e) {
            log.info("[AI 代办] 预约被业务规则拒绝：{}", e.getMessage());
            return "❌ 预约没有提交成功：" + e.getMessage()
                    + "\n\n可以换个时段或日期，或到「实时占用看板」看看空闲情况。";
        } catch (Exception e) {
            log.warn("[AI 代办] 预约提交异常", e);
            return "❌ 预约提交时出现异常，请稍后再试，或直接在「预约申请」页面提交。";
        }
    }

    private String bookReport(ReservationVO vo, LabInfo lab, SlotPick pick,
                              LocalDate wantedDate, LocalTime wanted, String preference) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 已提交预约申请，等待审批\n\n");
        sb.append("- 预约单号：").append(vo.getReservationNo()).append('\n');
        sb.append("- 实验室：").append(lab.getLabName()).append(labSuffix(lab)).append('\n');
        sb.append("- 时间：").append(formatDate(vo.getReserveDate())).append(' ')
                .append(hhmm(vo.getStartTime())).append(" - ").append(hhmm(vo.getEndTime())).append('\n');
        sb.append("- 人数：").append(vo.getPeopleCount()).append('\n');
        sb.append("- 用途：").append(vo.getPurpose()).append('\n');
        sb.append("- 状态：").append(vo.getStatusDesc()).append('\n');

        List<String> notes = new ArrayList<>();
        if (pick.dayOffset() > 0) {
            notes.add("你指定的 " + formatDate(wantedDate) + " 没有可约时段，已顺延到最近的 "
                    + formatDate(pick.date()) + "。");
        }
        if (wanted == null && preference == null) {
            notes.add("你没指定时段，我选了当天最早的 " + hhmm(vo.getStartTime()) + "-" + hhmm(vo.getEndTime()) + "。");
        }
        if (!notes.isEmpty()) {
            sb.append("\n说明：").append(String.join(" ", notes)).append('\n');
        }
        sb.append("\n审批通过后会生成 6 位核销码，可在「我的签到」查看；如需更换时段，先取消这条预约再重新提交。");
        return sb.toString();
    }

    /**
     * 找实验室：先看澄清回填的 ID，再按名称/别名匹配；多间命中时反问
     */
    private LabResolve resolveLab(AiActionPlan plan, String question, Long userId) {
        if (plan.getLabId() != null) {
            LabInfo lab = labInfoMapper.selectById(plan.getLabId());
            if (lab != null) {
                return new LabResolve(lab, null);
            }
        }
        String text = StrUtil.isNotBlank(plan.getLab()) ? plan.getLab() : question;
        List<LabInfo> matches = matchLabs(text);
        if (matches.isEmpty()) {
            return new LabResolve(null, "没找到你说的实验室。系统里有这些：\n" + labNameList()
                    + "\n直接把名称告诉我，例如「帮我预约本周五第一计算机实验室」。");
        }
        if (matches.size() > 1) {
            List<Option> options = matches.stream()
                    .limit(MAX_OPTIONS)
                    .map(lab -> new Option(lab.getId(), labLabel(lab), lab.getLabName()))
                    .toList();
            StringBuilder sb = new StringBuilder();
            sb.append("「").append(text).append("」能对上 ").append(matches.size()).append(" 间实验室，你想约哪一间？\n");
            for (int i = 0; i < options.size(); i++) {
                sb.append(i + 1).append(". ").append(options.get(i).getLabel()).append('\n');
            }
            sb.append("\n回个序号或说名称都行，我马上提交。");
            String prompt = sb.toString();
            savePending(userId, new Pending(TYPE_LAB, question, prompt, plan, options));
            return new LabResolve(null, prompt);
        }
        return new LabResolve(matches.getFirst(), null);
    }

    /**
     * 实验室匹配：全名/简称优先，其次按「实验室类型」召回（此时通常多间，交给澄清）
     */
    private List<LabInfo> matchLabs(String text) {
        List<LabInfo> labs = labInfoMapper.selectList(null);
        List<LabInfo> exact = new ArrayList<>();
        List<LabInfo> typed = new ArrayList<>();
        for (LabInfo lab : labs) {
            if (hitAlias(text, lab)) {
                exact.add(lab);
            } else if (StrUtil.isNotBlank(lab.getLabType()) && text.contains(lab.getLabType())) {
                typed.add(lab);
            }
        }
        if (!exact.isEmpty()) {
            // 名称越长越具体，排在前面
            exact.sort(Comparator.comparingInt((LabInfo l) -> l.getLabName().length()).reversed());
            return exact;
        }
        return typed;
    }

    private boolean hitAlias(String text, LabInfo lab) {
        for (String alias : aliases(lab)) {
            if (alias.length() >= 2 && text.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成实验室的常见叫法：「第一计算机实验室」→ 第一计算机 / 第一机房 / 一机房
     */
    private Set<String> aliases(LabInfo lab) {
        String name = StrUtil.blankToDefault(lab.getLabName(), "");
        Set<String> set = new LinkedHashSet<>();
        if (name.isEmpty()) {
            return set;
        }
        set.add(name);
        String core = NAME_SUFFIX.matcher(name).replaceAll("");
        set.add(core);
        set.add(core.replace("计算机", "").replace("实验室", ""));
        String room = name.replace("计算机实验室", "机房");
        set.add(room);
        set.add(room.replaceFirst("^第", ""));
        set.add(room.replaceFirst("^第", "第"));
        return set;
    }

    /**
     * 从指定日期起找最近的可约时段（指定日无空位时自动顺延）
     */
    private SlotPick pickWithFallback(LabInfo lab, LocalDate start, LocalTime wanted, String preference) {
        for (int offset = 0; offset < FALLBACK_DAYS; offset++) {
            LocalDate date = start.plusDays(offset);
            List<AvailableSlotVO> slots = usableSlots(lab.getId(), date);
            if (slots.isEmpty()) {
                continue;
            }
            AvailableSlotVO slot = pickSlot(slots, wanted, preference);
            if (slot != null) {
                return new SlotPick(date, slot, offset);
            }
        }
        return null;
    }

    /**
     * 某日真正可约的时段：排除已占用与今天已经结束的时段
     */
    private List<AvailableSlotVO> usableSlots(Long labId, LocalDate date) {
        List<AvailableSlotVO> slots = labTimeSlotService.availableSlots(labId, date).stream()
                .filter(slot -> !Boolean.TRUE.equals(slot.getOccupied()))
                .filter(slot -> slot.getStartTime() != null && slot.getEndTime() != null)
                .sorted(Comparator.comparing(AvailableSlotVO::getStartTime))
                .collect(Collectors.toCollection(ArrayList::new));
        if (date.equals(LocalDate.now())) {
            LocalTime now = LocalTime.now();
            slots.removeIf(slot -> !slot.getEndTime().isAfter(now));
        }
        return slots;
    }

    /**
     * 在可用时段里挑一个：显式时间 > 时段名称 > 上午/下午/晚上偏好 > 最早
     */
    private AvailableSlotVO pickSlot(List<AvailableSlotVO> slots, LocalTime wanted, String preference) {
        if (slots.isEmpty()) {
            return null;
        }
        if (wanted != null) {
            for (AvailableSlotVO slot : slots) {
                if (slot.getStartTime().equals(wanted)) {
                    return slot;
                }
            }
            // 用户口述的时间与整点时段有偏差，取最接近的一个
            return slots.stream()
                    .min(Comparator.comparingLong(s ->
                            Math.abs(Duration.between(s.getStartTime(), wanted).toMinutes())))
                    .orElse(slots.getFirst());
        }
        if (preference != null) {
            for (AvailableSlotVO slot : slots) {
                int hour = slot.getStartTime().getHour();
                boolean matched = switch (preference) {
                    case "morning" -> hour < 12;
                    case "noon" -> hour >= 11 && hour < 14;
                    case "afternoon" -> hour >= 12 && hour < 18;
                    case "evening" -> hour >= 18;
                    default -> false;
                };
                if (matched) {
                    return slot;
                }
            }
        }
        return slots.getFirst();
    }

    /**
     * 从文本里识别上下午偏好
     */
    private String preference(String timeText, String question) {
        String text = StrUtil.blankToDefault(timeText, "") + " " + question;
        if (containsAny(text, "上午", "早上", "早晨")) {
            return "morning";
        }
        if (containsAny(text, "中午", "午间")) {
            return "noon";
        }
        if (containsAny(text, "下午", "午后")) {
            return "afternoon";
        }
        if (containsAny(text, "晚上", "傍晚", "晚间")) {
            return "evening";
        }
        return null;
    }

    /* ==================== 取消预约 ==================== */

    private String cancel(AiActionPlan plan, String question, Long userId) {
        if (!SecurityUtil.hasPermission("reservation:cancel")) {
            return "❌ 当前账号没有取消预约的权限。";
        }
        LocalDate date = parseDate(StrUtil.isNotBlank(plan.getDateText()) ? plan.getDateText() : question);
        List<ReservationVO> active = reservationService.pageMyReservations(new Page<>(1, 50), null)
                .getRecords().stream()
                .filter(r -> "PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus()))
                .filter(r -> matchesReservation(r, plan, date))
                .toList();

        if (active.isEmpty()) {
            return "你名下没有符合这个条件的可取消预约（只有待审核、已通过的预约能取消）。"
                    + "可以到「我的预约」看看具体是哪一条，把单号或日期告诉我。";
        }
        if (active.size() > 1) {
            List<Option> options = active.stream()
                    .limit(MAX_OPTIONS)
                    .map(r -> new Option(r.getId(), reservationLabel(r), r.getReservationNo()))
                    .toList();
            StringBuilder sb = new StringBuilder();
            sb.append("你名下有 ").append(active.size()).append(" 条符合条件的预约，要取消哪一条？\n");
            for (int i = 0; i < options.size(); i++) {
                sb.append(i + 1).append(". ").append(options.get(i).getLabel()).append('\n');
            }
            sb.append("\n回个序号就行。");
            String prompt = sb.toString();
            savePending(userId, new Pending(TYPE_RESERVATION, question, prompt, plan, options));
            return prompt;
        }
        return doCancel(active.getFirst(), plan.getReason());
    }

    private String doCancel(ReservationVO target, String reason) {
        String cancelReason = StrUtil.isNotBlank(reason) ? reason : "AI 助手根据指令取消";
        try {
            reservationService.cancel(target.getId(), cancelReason);
            return "✅ 已取消预约 " + target.getReservationNo() + "\n\n"
                    + "- 实验室：" + target.getLabName() + '\n'
                    + "- 时间：" + formatDate(target.getReserveDate()) + ' '
                    + hhmm(target.getStartTime()) + " - " + hhmm(target.getEndTime()) + '\n'
                    + "- 状态：已取消\n\n该时段已释放，其他同学可以预约了。";
        } catch (BusinessException e) {
            return "❌ 没有取消成功：" + e.getMessage();
        } catch (Exception e) {
            log.warn("[AI 代办] 取消预约异常", e);
            return "❌ 取消时出现异常，请稍后再试，或到「我的预约」页面手动取消。";
        }
    }

    /**
     * 预约是否命中用户描述：单号/实验室/日期，有多个条件时取交集
     */
    private boolean matchesReservation(ReservationVO r, AiActionPlan plan, LocalDate date) {
        boolean hasCondition = false;
        boolean matched = true;
        String target = plan.getTarget();
        if (StrUtil.isNotBlank(target)) {
            hasCondition = true;
            matched = r.getReservationNo().contains(target)
                    || (r.getLabName() != null && r.getLabName().contains(target));
        }
        if (StrUtil.isNotBlank(plan.getLab())) {
            hasCondition = true;
            matched = matched && r.getLabName() != null && labTextMatch(r.getLabName(), plan.getLab());
        }
        if (date != null) {
            hasCondition = true;
            matched = matched && date.equals(r.getReserveDate());
        }
        return hasCondition ? matched : true;
    }

    /* ==================== 审批 ==================== */

    private String approve(AiActionPlan plan, String question, Long userId) {
        if (!SecurityUtil.hasPermission("reservation:approve")) {
            return "❌ 当前账号没有预约审批权限，无法代你审批。";
        }
        boolean reject = "REJECT".equalsIgnoreCase(plan.getDecision());
        String target = plan.getTarget();
        Long me = SecurityUtil.getUserId();
        List<ReservationVO> pending = reservationService.pageAll(new Page<>(1, 200), null, "PENDING", null)
                .getRecords().stream()
                .filter(r -> !Objects.equals(r.getStudentId(), me))
                .toList();
        if (pending.isEmpty()) {
            return "当前没有可审批的待审核预约（自己的单据不能自审）。";
        }

        List<ReservationVO> candidates;
        if (plan.getReservationId() != null) {
            candidates = pending.stream().filter(r -> r.getId().equals(plan.getReservationId())).toList();
        } else if (StrUtil.isNotBlank(target)) {
            candidates = pending.stream()
                    .filter(r -> r.getReservationNo().contains(target)
                            || (r.getStudentName() != null && r.getStudentName().contains(target)))
                    .toList();
            if (candidates.isEmpty()) {
                return "没找到和「" + target + "」相关的待审核预约（自己的单据不能自审）。";
            }
        } else if (Boolean.TRUE.equals(plan.getAll())) {
            candidates = pending;
        } else {
            // 既没点名也没说「全部」，不擅自批量处理：先列出来让用户确认
            List<Option> options = pending.stream()
                    .limit(MAX_OPTIONS)
                    .map(r -> new Option(r.getId(), reservationLabel(r), r.getReservationNo()))
                    .toList();
            StringBuilder sb = new StringBuilder();
            sb.append("现在有 ").append(pending.size()).append(" 条待审核预约，要处理哪一条？\n");
            for (int i = 0; i < options.size(); i++) {
                sb.append(i + 1).append(". ").append(options.get(i).getLabel()).append('\n');
            }
            sb.append("\n回个序号就行；也可以说「全部").append(reject ? "驳回" : "通过").append("」。");
            String prompt = sb.toString();
            savePending(userId, new Pending(TYPE_RESERVATION, question, prompt, plan, options));
            return prompt;
        }

        List<ReservationVO> batch = candidates.size() > MAX_APPROVE_BATCH
                ? candidates.subList(0, MAX_APPROVE_BATCH)
                : candidates;
        String comment = StrUtil.isNotBlank(plan.getReason())
                ? plan.getReason()
                : (reject ? "AI 助手根据指令驳回" : "AI 助手根据指令通过");
        int success = 0;
        int skipped = 0;
        List<String> lines = new ArrayList<>();
        for (ReservationVO r : batch) {
            ApprovalDTO dto = new ApprovalDTO();
            dto.setReservationId(r.getId());
            dto.setAction(reject ? "REJECT" : "APPROVE");
            dto.setComment(comment);
            try {
                reservationService.approve(dto);
                success++;
                lines.add("- " + r.getReservationNo() + "｜" + StrUtil.blankToDefault(r.getStudentName(), "-")
                        + "｜" + r.getLabName() + "｜" + formatDate(r.getReserveDate()) + ' '
                        + hhmm(r.getStartTime()) + "-" + hhmm(r.getEndTime())
                        + "｜" + (reject ? "已驳回" : "已通过"));
            } catch (BusinessException e) {
                skipped++;
                lines.add("- " + r.getReservationNo() + "｜跳过：" + e.getMessage());
            } catch (Exception e) {
                skipped++;
                log.warn("[AI 代办] 审批异常 id={}", r.getId(), e);
                lines.add("- " + r.getReservationNo() + "｜跳过：处理异常");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(reject ? "✅ 已驳回 " : "✅ 已通过 ").append(success).append(" 条预约");
        if (skipped > 0) {
            sb.append("，跳过 ").append(skipped).append(" 条");
        }
        sb.append("\n\n").append(String.join("\n", lines));
        if (candidates.size() > MAX_APPROVE_BATCH) {
            sb.append("\n\n还有 ").append(candidates.size() - MAX_APPROVE_BATCH)
                    .append(" 条待审核（单次最多处理 ").append(MAX_APPROVE_BATCH)
                    .append(" 条），再跟我说一次就继续。");
        }
        return sb.toString();
    }

    /* ==================== 报修 ==================== */

    private String repair(AiActionPlan plan, String question, Long userId) {
        if (!SecurityUtil.hasPermission("device:repair:submit")) {
            return "❌ 当前账号没有提交报修的权限。";
        }
        List<RepairDeviceOptionVO> options = deviceRepairService.deviceOptions();
        RepairDeviceOptionVO device = null;
        if (plan.getDeviceId() != null) {
            device = options.stream().filter(d -> d.getId().equals(plan.getDeviceId())).findFirst().orElse(null);
        }
        if (device == null) {
            String keyword = StrUtil.isNotBlank(plan.getDevice()) ? plan.getDevice() : question;
            List<RepairDeviceOptionVO> matches = options.stream()
                    .filter(d -> deviceMatch(d, keyword))
                    .toList();
            if (matches.isEmpty() && StrUtil.isNotBlank(plan.getLab())) {
                matches = options.stream()
                        .filter(d -> d.getLabName() != null && labTextMatch(d.getLabName(), plan.getLab()))
                        .toList();
            }
            if (matches.isEmpty()) {
                return "没找到你说的设备。可以说设备名称或编号（例如「示波器」「PC-101-03」），"
                        + "也可以说实验室名，我再帮你找这台设备。";
            }
            if (matches.size() > 1) {
                List<Option> clarifyOptions = matches.stream()
                        .limit(MAX_OPTIONS)
                        .map(d -> new Option(d.getId(), deviceLabel(d), d.getDeviceName()))
                        .toList();
                StringBuilder sb = new StringBuilder();
                sb.append("能对上 ").append(matches.size()).append(" 台设备，是哪一台？\n");
                for (int i = 0; i < clarifyOptions.size(); i++) {
                    sb.append(i + 1).append(". ").append(clarifyOptions.get(i).getLabel()).append('\n');
                }
                sb.append("\n回个序号就行。");
                String prompt = sb.toString();
                savePending(userId, new Pending(TYPE_DEVICE, question, prompt, plan, clarifyOptions));
                return prompt;
            }
            device = matches.getFirst();
        }

        String desc = StrUtil.isNotBlank(plan.getFaultDesc())
                ? plan.getFaultDesc()
                : StrUtil.maxLength(question, 200);
        DeviceRepairDTO dto = new DeviceRepairDTO();
        dto.setDeviceId(device.getId());
        dto.setFaultDesc(desc);
        try {
            RepairVO vo = deviceRepairService.submit(dto);
            return "✅ 已提交报修\n\n"
                    + "- 报修单号：" + vo.getRepairNo() + '\n'
                    + "- 设备：" + vo.getDeviceName() + '\n'
                    + "- 故障描述：" + vo.getFaultDesc() + '\n'
                    + "- 状态：" + vo.getStatusDesc() + "\n\n"
                    + "设备与所属实验室会暂时锁定预约，维修完成或取消报修后自动解锁。";
        } catch (BusinessException e) {
            return "❌ 报修没有提交成功：" + e.getMessage();
        } catch (Exception e) {
            log.warn("[AI 代办] 报修提交异常", e);
            return "❌ 报修提交时出现异常，请稍后再试，或到「设备报修」页面手动提交。";
        }
    }

    private boolean deviceMatch(RepairDeviceOptionVO d, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return false;
        }
        return (d.getDeviceName() != null && keyword.contains(d.getDeviceName()))
                || (d.getDeviceCode() != null && StrUtil.containsIgnoreCase(keyword, d.getDeviceCode()))
                || (d.getDeviceName() != null && StrUtil.containsIgnoreCase(d.getDeviceName(), keyword));
    }

    /* ==================== 澄清上下文 ==================== */

    /**
     * 尝试把用户这句话当成上一轮追问的答案
     */
    private String applyPending(Pending pending, String answer, Long userId) {
        Option hit = resolveOption(answer, pending.getOptions());
        if (hit == null) {
            if (TYPE_DATE.equals(pending.getType())) {
                LocalDate date = parseDate(answer);
                if (date != null) {
                    pending.getPlan().setDateText(date.toString());
                    return execute(pending.getPlan(), pending.getQuestion(), userId);
                }
            }
            return null;
        }
        switch (pending.getType()) {
            case TYPE_LAB -> pending.getPlan().setLabId(hit.getId());
            case TYPE_RESERVATION -> pending.getPlan().setReservationId(hit.getId());
            case TYPE_DEVICE -> pending.getPlan().setDeviceId(hit.getId());
            default -> {
                return null;
            }
        }
        return execute(pending.getPlan(), pending.getQuestion(), userId);
    }

    private Option resolveOption(String answer, List<Option> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        // 先按名称/单号匹配，避免「第二计算机」被当成序号 2
        Option best = null;
        int bestLen = 0;
        for (Option option : options) {
            for (String key : new String[]{option.getKeyword(), option.getLabel()}) {
                if (StrUtil.isNotBlank(key) && answer.contains(key) && key.length() > bestLen) {
                    best = option;
                    bestLen = key.length();
                }
            }
        }
        if (best != null) {
            return best;
        }
        Matcher m = ORDINAL.matcher(answer);
        if (!m.find()) {
            return null;
        }
        int index = ordinalValue(m.group(1));
        return index >= 1 && index <= options.size() ? options.get(index - 1) : null;
    }

    private int ordinalValue(String text) {
        return switch (text) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> StrUtil.isNumeric(text) ? Integer.parseInt(text) : -1;
        };
    }

    /**
     * 短回答（「第二个」「就第一个」）视为澄清；长句大概率是新指令
     */
    private boolean isShortAnswer(String answer) {
        return answer.length() <= 16 && !containsAny(answer, "预约", "审批", "报修", "取消", "帮我", "我想");
    }

    private Pending readPending(Long userId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeyConstants.aiActionPendingKey(userId));
            if (StrUtil.isBlank(json)) {
                return null;
            }
            JSONObject obj = JSONUtil.parseObj(json);
            Pending pending = new Pending();
            pending.setType(obj.getStr("type"));
            pending.setQuestion(obj.getStr("question"));
            pending.setPrompt(obj.getStr("prompt"));
            JSONObject planJson = obj.getJSONObject("plan");
            pending.setPlan(planJson == null ? new AiActionPlan() : planJson.toBean(AiActionPlan.class));
            JSONArray arr = obj.getJSONArray("options");
            if (arr != null) {
                pending.setOptions(arr.stream()
                        .filter(JSONObject.class::isInstance)
                        .map(o -> ((JSONObject) o).toBean(Option.class))
                        .toList());
            }
            return pending;
        } catch (Exception e) {
            log.warn("[AI 代办] 读取澄清上下文失败", e);
            return null;
        }
    }

    private void savePending(Long userId, Pending pending) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.aiActionPendingKey(userId),
                    JSONUtil.toJsonStr(pending), PENDING_TTL);
        } catch (Exception e) {
            log.warn("[AI 代办] 保存澄清上下文失败", e);
        }
    }

    private void clearPending(Long userId) {
        try {
            stringRedisTemplate.delete(RedisKeyConstants.aiActionPendingKey(userId));
        } catch (Exception ignored) {
            // 清理失败不影响主流程，键会随 TTL 过期
        }
    }

    /* ==================== 文本与日期解析 ==================== */

    /**
     * 从自然语言里解析预约日期（供单测直接调用）
     */
    static LocalDate parseDate(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
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

        Matcher monthDay = MONTH_DAY.matcher(question);
        if (monthDay.find()) {
            LocalDate date = safeDate(today.getYear(), Integer.parseInt(monthDay.group(1)),
                    Integer.parseInt(monthDay.group(2)));
            if (date != null && date.isBefore(today)) {
                date = safeDate(today.getYear() + 1, Integer.parseInt(monthDay.group(1)),
                        Integer.parseInt(monthDay.group(2)));
            }
            return date;
        }

        DayOfWeek dayOfWeek = parseWeekDay(question);
        if (dayOfWeek != null) {
            boolean nextNextWeek = question.contains("下下周") || question.contains("下下个星期");
            boolean nextWeek = !nextNextWeek
                    && (question.contains("下周") || question.contains("下个星期"));
            boolean thisWeek = question.contains("本周") || question.contains("这周") || question.contains("这个星期");
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate target = monday.plusDays(dayOfWeek.getValue() - 1L);
            if (nextNextWeek) {
                target = target.plusWeeks(2);
            } else if (nextWeek) {
                target = target.plusWeeks(1);
            } else if (!thisWeek && target.isBefore(today)) {
                // 只说「周五」且本周五已过，理解为下一个周五
                target = target.plusWeeks(1);
            }
            return target;
        }
        if (question.contains("周末")) {
            return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
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
     * 解析用户口述的时间，如「9点」「08:00」「下午2点」「第二节」
     */
    private static LocalTime parseTime(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        Matcher section = SECTION.matcher(question);
        if (section.find()) {
            LocalTime start = SECTION_START.get(section.group(1));
            if (start != null) {
                return start;
            }
        }
        Matcher m = CLOCK.matcher(question);
        if (!m.find()) {
            return null;
        }
        Integer hourValue = AiIntentParser.cnNumber(m.group(2));
        if (hourValue == null) {
            return null;
        }
        int hour = hourValue;
        int minute = 0;
        if (m.group(3) != null) {
            Integer minuteValue = AiIntentParser.cnNumber(m.group(3));
            minute = minuteValue == null ? 0 : minuteValue;
        } else if (m.group(4) != null) {
            // 「两点半」
            minute = 30;
        }
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
        return time == null ? "" : String.valueOf(time).substring(0, 5);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 实验室名与口语片段是否匹配（用于取消/报修里的二次筛选）
     */
    private boolean labTextMatch(String labName, String text) {
        if (labName.contains(text) || text.contains(labName)) {
            return true;
        }
        String core = NAME_SUFFIX.matcher(labName).replaceAll("");
        return core.length() >= 2 && (text.contains(core) || core.contains(text));
    }

    private String labSuffix(LabInfo lab) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(lab.getLabCode())) {
            parts.add(lab.getLabCode());
        }
        if (StrUtil.isNotBlank(lab.getLabType())) {
            parts.add(lab.getLabType() + "类");
        }
        if (StrUtil.isNotBlank(lab.getLocation())) {
            parts.add(lab.getLocation());
        }
        return parts.isEmpty() ? "" : "（" + String.join(" · ", parts) + "）";
    }

    private String labLabel(LabInfo lab) {
        return lab.getLabName() + labSuffix(lab);
    }

    private String labNameList() {
        List<LabInfo> labs = labInfoMapper.selectList(null);
        return labs.stream().map(lab -> "- " + lab.getLabName()).collect(Collectors.joining("\n"));
    }

    private String reservationLabel(ReservationVO r) {
        return r.getReservationNo() + "｜" + r.getLabName() + "｜" + formatDate(r.getReserveDate())
                + " " + hhmm(r.getStartTime()) + "-" + hhmm(r.getEndTime()) + "｜" + r.getStatusDesc();
    }

    private String deviceLabel(RepairDeviceOptionVO d) {
        return d.getDeviceName() + "（" + StrUtil.blankToDefault(d.getDeviceCode(), "-")
                + " · " + StrUtil.blankToDefault(d.getLabName(), "-") + "）";
    }

    /* ==================== 内部数据结构 ==================== */

    private record LabResolve(LabInfo lab, String clarify) {
    }

    private record SlotPick(LocalDate date, AvailableSlotVO slot, int dayOffset) {
    }

    /** 待用户澄清的上下文 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pending {
        private String type;
        private String question;
        private String prompt;
        private AiActionPlan plan;
        private List<Option> options;
    }

    /** 候选项（实验室 / 预约 / 设备） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String label;
        private String keyword;
    }
}
