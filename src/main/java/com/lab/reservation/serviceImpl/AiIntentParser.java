package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.lab.reservation.dto.AiActionPlan;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 代办指令解析器
 *
 * <p>把「帮我约明天下午去一机房写代码」「把张三那单批了」这类口语解析成结构化指令。
 * 这里是纯本地解析，不做任何远程调用 —— 原因很实在：上游模型单次非流式响应实测要 10 秒左右，
 * 放在指令执行前会让每个操作都卡十秒，体验远不如本地规则。</p>
 *
 * <p>因此解析层负责「听懂话」，执行层（{@code AiActionServiceImpl}）负责「办对事」：
 * 实验室别名、日期表达、时段偏好、人数用途等都在本地即时折算，
 * 权限与业务校验则一律交回原业务服务。</p>
 *
 * @author LabWise
 * @since 3.0.0
 */
@Component
public class AiIntentParser {

    /** 「把张三的预约批了」→ 张三 */
    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?:把|将|给)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}?)(?:同学|老师)?(?:的|那|这)?(?:预约|申请|单)");

    /** 「驳回张三的预约」→ 张三（没有「把」字时的补充规则） */
    private static final Pattern TARGET_VERB_PATTERN = Pattern.compile(
            "(?:驳回|拒绝|通过|批准|审批|审核)\\s*([\\u4e00-\\u9fa5]{2,4})的(?:预约|申请)");

    /** 「RSV202610290132005A42 批了」→ 单号 */
    private static final Pattern RESERVATION_NO_PATTERN = Pattern.compile("([A-Z]{2,4}\\d{8,}[A-Za-z0-9]*)");

    /** 「理由是设备检修」→ 设备检修 */
    private static final Pattern REASON_PATTERN = Pattern.compile("(?:理由|原因)(?:是|为)?\\s*([^，。,.]{2,60})");

    /** 「3人」「两个人」→ 人数 */
    private static final Pattern PEOPLE_PATTERN = Pattern.compile("([0-9]{1,3}|[一二两三四五六七八九十]{1,3})\\s*(?:个人|人)");

    /** 常见预约用途，用于从口语里直接抽取 */
    private static final String[] PURPOSES = {"写代码", "做实验", "上课", "开会", "讨论", "练习",
            "考试", "培训", "答辩", "自习", "小组作业", "项目开发"};

    private static final Map<String, Integer> CN_NUMBERS = Map.ofEntries(
            Map.entry("一", 1), Map.entry("二", 2), Map.entry("两", 2), Map.entry("三", 3),
            Map.entry("四", 4), Map.entry("五", 5), Map.entry("六", 6), Map.entry("七", 7),
            Map.entry("八", 8), Map.entry("九", 9), Map.entry("十", 10));

    /**
     * 粗筛：是否可能是一条操作指令
     *
     * <p>先用关键词挡掉绝大多数咨询类问题（「预约规则是什么」「怎么签到」），
     * 只把可能带动作的句子交给后面的解析与执行。</p>
     */
    public boolean looksOperational(String question) {
        boolean hasAction = containsAny(question, "预约", "约", "订", "审批", "审核", "批准", "批",
                "驳回", "拒绝", "通过", "取消", "退订", "报修", "维修", "坏了", "故障", "开不了机", "无法开机");
        if (!hasAction) {
            return false;
        }
        boolean questionLike = containsAny(question, "怎么", "如何", "为什么", "是什么", "哪些", "规则", "流程",
                "介绍", "区别", "吗", "？", "?", "能不能", "可以吗", "是否", "多少", "哪里", "在哪",
                "状态", "进度", "记录", "列表", "查", "有没有", "多久", "什么");
        return !questionLike;
    }

    /**
     * 解析动作与能从原句直接抽出的参数
     *
     * <p>实验室、日期、时段等依赖系统数据的参数由执行器再解析一次，
     * 这里只做「这句话想干什么」的判断。</p>
     */
    public AiActionPlan parse(String question) {
        AiActionPlan plan = new AiActionPlan();
        if (containsAny(question, "审批", "审核", "批准", "驳回", "拒绝", "批一下", "批了", "通过")) {
            plan.setAction(AiActionPlan.APPROVE);
            plan.setDecision(containsAny(question, "驳回", "拒绝", "不批", "不通过") ? "REJECT" : "APPROVE");
            plan.setAll(containsAny(question, "所有", "全部", "都"));
            plan.setTarget(extractTarget(question));
            plan.setReason(extractReason(question));
            return plan;
        }
        if (containsAny(question, "取消", "退订", "不去了")) {
            plan.setAction(AiActionPlan.CANCEL);
            plan.setTarget(extractTarget(question));
            return plan;
        }
        if (containsAny(question, "报修", "维修", "坏了", "故障", "开不了机", "无法开机")) {
            plan.setAction(AiActionPlan.REPAIR);
            plan.setFaultDesc(cleanFault(question));
            return plan;
        }
        if (containsAny(question, "预约", "约", "订")) {
            plan.setAction(AiActionPlan.BOOK);
            plan.setPurpose(extractPurpose(question));
            plan.setPeopleCount(extractPeople(question));
            return plan;
        }
        plan.setAction(AiActionPlan.NONE);
        return plan;
    }

    private String extractTarget(String question) {
        Matcher verb = TARGET_VERB_PATTERN.matcher(question);
        if (verb.find()) {
            return cleanTarget(verb.group(1));
        }
        Matcher m = TARGET_PATTERN.matcher(question);
        if (m.find()) {
            return cleanTarget(m.group(1));
        }
        Matcher no = RESERVATION_NO_PATTERN.matcher(question);
        if (no.find()) {
            return no.group(1);
        }
        return null;
    }

    /**
     * 过滤掉「所有/我自己」这类不是人名的片段
     */
    private String cleanTarget(String raw) {
        String target = raw.trim();
        if (containsAny(target, "所有", "全部", "待审核", "这些", "那些", "我自己", "我", "自己", "它", "这", "那")) {
            return null;
        }
        return target.length() >= 2 ? target : null;
    }

    /**
     * 从「驳回，理由是设备检修」里抽出驳回原因
     */
    private String extractReason(String question) {
        Matcher m = REASON_PATTERN.matcher(question);
        return m.find() ? StrUtil.maxLength(m.group(1).trim(), 100) : null;
    }

    /**
     * 从「数字示波器开不了机了，帮我报修」里去掉指令部分，只留故障描述
     */
    private String cleanFault(String question) {
        String text = question
                .replaceAll("[，,。.！!？?；;\\s]+$", "")
                .replaceAll("(麻烦|请)?\\s*(帮我|帮忙|给我)?\\s*(提交|申请)?\\s*(报修|维修|修一下|修一修|处理一下)(一下)?$", "")
                .replaceAll("^(师傅|你好|您好|麻烦|请|帮我|帮忙|给我)\\s*", "")
                // 去掉指令词后，前面可能留下一个逗号
                .replaceAll("[，,。.！!？?；;\\s]+$", "");
        return StrUtil.maxLength(text.trim(), 200);
    }

    /**
     * 从「去机房写代码」里抽用途
     */
    private String extractPurpose(String question) {
        for (String purpose : PURPOSES) {
            if (question.contains(purpose)) {
                return purpose;
            }
        }
        return null;
    }

    /**
     * 从「我和同学两个人」「3人」里抽人数
     */
    private Integer extractPeople(String question) {
        Matcher m = PEOPLE_PATTERN.matcher(question);
        if (m.find()) {
            Integer number = cnNumber(m.group(1));
            if (number != null && number >= 1 && number <= 300) {
                return number;
            }
        }
        if (containsAny(question, "我和同学", "我们俩", "两个人")) {
            return 2;
        }
        return null;
    }

    /**
     * 中文数字转整数（支持「两」「十」「十二」「二十」）
     */
    static Integer cnNumber(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        if (StrUtil.isNumeric(text)) {
            return Integer.parseInt(text);
        }
        Integer single = CN_NUMBERS.get(text);
        if (single != null) {
            return single;
        }
        int tenIndex = text.indexOf('十');
        if (tenIndex < 0) {
            return null;
        }
        int tens = tenIndex == 0 ? 1 : CN_NUMBERS.getOrDefault(text.substring(0, 1), 0);
        int ones = tenIndex == text.length() - 1 ? 0 : CN_NUMBERS.getOrDefault(text.substring(tenIndex + 1), 0);
        return tens * 10 + ones;
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
