package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.KnowledgeRanker;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.config.AiProperties;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.entity.AiKnowledge;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.DeviceRepair;
import com.lab.reservation.entity.LabCheckin;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.LabTimeSlot;
import com.lab.reservation.entity.SysNotice;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.CheckinStatus;
import com.lab.reservation.enums.RepairStatus;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.AiStreamAbortedException;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.AiKnowledgeMapper;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.DeviceRepairMapper;
import com.lab.reservation.mapper.LabCheckinMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.LabTimeSlotMapper;
import com.lab.reservation.mapper.SysNoticeMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.service.AiActionService;
import com.lab.reservation.service.AiAssistantService;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.vo.AiMessageVO;
import com.lab.reservation.vo.BoardSummaryVO;
import com.lab.reservation.vo.HotLabVO;
import com.lab.reservation.vo.LabBoardVO;
import com.lab.reservation.vo.StatsOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 助手服务实现
 *
 * <p><b>答辩亮点：流式代理 + 知识库检索 + 上下文注入 + 会话缓存</b></p>
 *
 * <p>几个关键的工程处理（都是流式代理的常见坑）：</p>
 * <ol>
 *   <li><b>不使用 HttpRequest 级别的 timeout</b>：JDK 的该超时覆盖整个请求交换（含响应体），
 *       会把长回答中途截断；只靠 HttpClient 的 connectTimeout 与本地看门狗</li>
 *   <li><b>逐行解析 SSE</b>：只认 {@code data:} 行，跳过心跳/事件名/空行；
 *       单行解析失败只记日志不中断整条流</li>
 *   <li><b>只转发正文增量</b>：上游会先流式输出 {@code delta.reasoning}（模型思考过程），
 *       必须跳过，否则用户会看到模型的内部推理</li>
 *   <li><b>会话历史用 Redis List</b>：push + trim + expire，避免「单键 JSON 读改写」在并发下丢消息</li>
 *   <li><b>知识库检索</b>：{@code ai_knowledge} 表按关键词计分取 TOP5 注入提示词，
 *       条目列表 Redis 整体缓存 10 分钟，知识变更无需重启</li>
 * </ol>
 *
 * @author LabWise
 * @since 1.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final AiProperties aiProperties;
    private final HttpClient aiHttpClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final StatisticsService statisticsService;
    private final LabReservationMapper reservationMapper;
    private final LabCheckinMapper checkinMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceRepairMapper repairMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final LabInfoMapper labInfoMapper;
    private final LabTimeSlotMapper slotMapper;
    private final SysNoticeMapper noticeMapper;
    private final AiActionService aiActionService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * AI 流看门狗调度器
     *
     * <p>单线程守护调度器：所有对话的空闲检测共用它，而不是每次请求新建一个线程。
     * 看门狗只做「每 500ms 检查一次是否空闲超时」这种极轻量工作，单线程足够。</p>
     */
    private static final ScheduledExecutorService AI_WATCHDOG = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "ai-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** 会话历史缓存时长 */
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    /** 上下文注入的总字符上限，避免 prompt 过大（知识库 + 数据注入后较此前更宽） */
    private static final int CONTEXT_MAX_LENGTH = 12000;

    /** 单类数据注入的条数上限 */
    private static final int ROW_LIMIT = 10;

    /** 知识库单次注入的条数上限 */
    private static final int KB_LIMIT = 5;

    /** 知识库缓存时长 */
    private static final Duration KB_TTL = Duration.ofMinutes(10);

    /** 角色编码 -> 中文名 */
    private static final Map<String, String> ROLE_NAMES = Map.of(
            "SUPER_ADMIN", "超级管理员",
            "LAB_ADMIN", "实验室管理员",
            "TEACHER", "教师",
            "STUDENT", "学生");

    @Override
    public List<AiMessageVO> history() {
        return readHistory(SecurityUtil.getUserId());
    }

    @Override
    public void clearHistory() {
        stringRedisTemplate.delete(RedisKeyConstants.aiChatKey(SecurityUtil.getUserId()));
    }

    @Override
    public String streamChat(Long userId, String question, Consumer<String> onDelta) {
        if (!aiProperties.isConfigured()) {
            throw new BusinessException(ResultCode.AI_NOT_CONFIGURED);
        }
        // 1. 限流（每分钟计数，首次写入时设过期）
        String rateKey = RedisKeyConstants.aiRateKey(userId);
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, Duration.ofMinutes(1));
        }
        if (count != null && count > aiProperties.getRateLimitPerMinute()) {
            throw new BusinessException(ResultCode.AI_TOO_FREQUENT);
        }
        // 2. 同一用户同时只允许一条流（TTL 兜底，进程异常退出也不会永久占用）
        String busyKey = RedisKeyConstants.aiBusyKey(userId);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(busyKey, "1", Duration.ofSeconds(120));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ResultCode.AI_BUSY);
        }
        try {
            // 3. 代办指令优先：识别到「帮我预约/帮我审批」这类祈使句时直接执行业务动作，
            //    不再调用上游模型（结果确定、省 token，且权限/冲突校验仍由原服务把关）
            AiActionService.Result action = aiActionService.handle(question);
            if (action.matched()) {
                streamText(action.report(), onDelta);
                appendHistory(userId, question, action.report());
                return action.report();
            }
            List<AiMessageVO> history = readHistory(userId);
            JSONArray messages = buildMessages(userId, question, history);
            String answer = callUpstream(messages, onDelta);
            appendHistory(userId, question, answer);
            return answer;
        } finally {
            stringRedisTemplate.delete(busyKey);
        }
    }

    /**
     * 把确定性文本按打字机节奏推给前端（代办结果不是模型流，需自行分块）
     */
    private void streamText(String text, Consumer<String> onDelta) {
        if (StrUtil.isBlank(text) || onDelta == null) {
            return;
        }
        int chunkSize = 28;
        for (int i = 0; i < text.length(); i += chunkSize) {
            String chunk = text.substring(i, Math.min(i + chunkSize, text.length()));
            onDelta.accept(chunk);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /* ==================== 上游调用 ==================== */

    /**
     * 调用上游模型并逐行解析 SSE，返回完整正文
     */
    private String callUpstream(JSONArray messages, Consumer<String> onDelta) {
        JSONObject payload = JSONUtil.createObj()
                .set("model", aiProperties.getModel())
                .set("stream", true)
                .set("max_tokens", 2048)
                .set("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiProperties.chatCompletionsUrl()))
                .header("Authorization", "Bearer " + aiProperties.getToken())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                // 注意：此处刻意不设置 timeout()，见类注释第 1 点
                .build();

        long idleMillis = aiProperties.getTimeoutSeconds() * 1000L;
        // 总时长硬上限：仅用于兜底防止连接无限挂起，正常长回答（数分钟）不会触及
        long totalMillis = Math.max(idleMillis * 10, 600_000L);
        long startedAt = System.currentTimeMillis();
        // 看门狗计「空闲」而非「总时长」：流式回答可能持续数分钟，
        // 只要上游还在持续吐数据（哪怕只是思考过程/心跳），就不该中断；
        // 只有连续 timeoutSeconds 秒完全收不到任何数据才判定卡死。
        AtomicLong lastActivity = new AtomicLong(startedAt);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        StringBuilder answer = new StringBuilder();
        try {
            HttpResponse<InputStream> response =
                    aiHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    String err = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    log.warn("[AI] 上游返回 {}：{}", response.statusCode(), StrUtil.maxLength(err, 500));
                    throw new BusinessException(ResultCode.AI_UPSTREAM_ERROR);
                }
                // readLine() 会一直阻塞，循环里没法自检空闲，只能由旁路线程在超时后关闭流，
                // 让阻塞中的读取抛异常退出（这是「不设 HttpRequest 级 timeout」的配套兜底）。
                // 这里复用共享调度池：每次对话都 new Thread 会让线程数随并发线性增长
                ScheduledFuture<?> watchdog = AI_WATCHDOG.schedule(new Runnable() {
                    @Override
                    public void run() {
                        long now = System.currentTimeMillis();
                        if (now - lastActivity.get() > idleMillis || now - startedAt > totalMillis) {
                            timedOut.set(true);
                            log.warn("[AI] 上游连续 {} 秒无数据（或超过总时长上限），主动中断",
                                    aiProperties.getTimeoutSeconds());
                            try {
                                in.close();
                            } catch (Exception ignored) {
                                // 关闭失败也无所谓，读取侧会自行异常退出
                            }
                            return;
                        }
                        // 尚未超时：继续巡检（同一条线程池线程，不新增线程）
                        AI_WATCHDOG.schedule(this, 500, TimeUnit.MILLISECONDS);
                    }
                }, 500, TimeUnit.MILLISECONDS);
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 任何一行到达（正文/思考过程/心跳）都算「有活动」
                        lastActivity.set(System.currentTimeMillis());
                        String delta = extractDelta(line);
                        if (StrUtil.isBlank(delta)) {
                            continue;
                        }
                        answer.append(delta);
                        onDelta.accept(delta);
                    }
                } finally {
                    watchdog.cancel(false);
                }
            }
        } catch (BusinessException | AiStreamAbortedException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.AI_UPSTREAM_ERROR);
        } catch (Exception e) {
            // 看门狗关流导致的 IOException 是预期结果，不打堆栈
            if (timedOut.get()) {
                throw new BusinessException(ResultCode.AI_UPSTREAM_ERROR);
            }
            log.error("[AI] 调用上游失败", e);
            throw new BusinessException(ResultCode.AI_UPSTREAM_ERROR);
        }
        return answer.toString();
    }

    /**
     * 从一行 SSE 文本中取出正文增量；非正文行返回 null
     *
     * <p>只认 {@code data:} 行（注意前缀长度为 5，可能紧跟内容而非空格），
     * 跳过空行、注释心跳（{@code :}）、{@code event:} / {@code id:} 行；
     * 上游的 {@code delta.reasoning}（思考过程）与 role/usage 等字段一律不转发。</p>
     */
    private String extractDelta(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(":") || !trimmed.startsWith("data:")) {
            return null;
        }
        String payload = trimmed.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload) || !JSONUtil.isTypeJSON(payload)) {
            return null;
        }
        try {
            JSONObject chunk = JSONUtil.parseObj(payload);
            JSONArray choices = chunk.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            // 只取正文；delta 里的 role / reasoning（思考过程）/ 其它字段一律忽略
            Object content = delta.get("content");
            return content == null ? null : content.toString();
        } catch (Exception e) {
            // 单行解析异常不影响整条流
            log.debug("[AI] 跳过无法解析的上游片段：{}", StrUtil.maxLength(payload, 200));
            return null;
        }
    }

    /* ==================== 上下文构建 ==================== */

    /**
     * 组装发送给模型的消息列表：system（含当前用户与系统数据） + 历史 + 本次提问
     */
    private JSONArray buildMessages(Long userId, String question, List<AiMessageVO> history) {
        JSONArray messages = new JSONArray();
        messages.add(JSONUtil.createObj()
                .set("role", "system")
                .set("content", buildSystemPrompt(userId, question)));
        int from = Math.max(0, history.size() - aiProperties.getMaxHistory());
        for (AiMessageVO item : history.subList(from, history.size())) {
            messages.add(JSONUtil.createObj().set("role", item.getRole()).set("content", item.getContent()));
        }
        messages.add(JSONUtil.createObj().set("role", "user").set("content", question));
        return messages;
    }

    /**
     * 系统提示词：角色设定 + 当前用户 + 知识库命中条目 + 按提问选择性注入的系统数据
     */
    private String buildSystemPrompt(Long userId, String question) {
        StringBuilder sb = new StringBuilder("""
                你是「智慧实验室预约管理系统」内置的 AI 助手。
                系统功能包括：实验室预约（提交/审核/取消）、开放时段配置、实验室实时占用看板、
                预约签到核销、设备台账与报修闭环、站内消息通知、数据驾驶舱统计。

                回答要求：
                1. 用简体中文回答，简洁专业；
                2. 涉及系统的规则、流程、操作步骤时，优先依据下方【系统知识库】作答；涉及系统实时数据时只能依据【系统数据】作答；两者都没有的内容就说明「暂无相关资料」，不要编造；
                3. 需要用户操作时，指出具体页面，例如「预约申请」页、「我的签到」页；
                4. 与系统无关的通用问题照常回答即可；
                5. 排版要求：不要输出 Markdown 标题符号（#），小节直接用文字编号（一、二、三 或 1. 2. 3.）；
                   重要词可用加粗，强调内容用「」引号；可以用 emoji 适当点缀分区，但不要堆砌。
                """);

        SysUser user = sysUserMapper.selectById(userId);
        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        String roleText = roleCodes == null || roleCodes.isEmpty()
                ? "未知"
                : roleCodes.stream().map(c -> ROLE_NAMES.getOrDefault(c, c)).collect(Collectors.joining("/"));
        sb.append("\n【当前用户】姓名：")
                .append(user == null ? "未知" : user.getRealName())
                .append("；角色：").append(roleText);

        String knowledge = buildKnowledgeBlock(question);
        sb.append("\n【系统知识库】\n")
                .append(StrUtil.blankToDefault(knowledge, "本次提问未命中知识库条目"));

        String data = buildDataBlock(userId, roleCodes, question);
        sb.append("\n【系统数据】\n").append(StrUtil.blankToDefault(data, "本次提问未涉及系统数据"));
        return StrUtil.maxLength(sb.toString(), CONTEXT_MAX_LENGTH);
    }

    /* ==================== 知识库检索 ==================== */

    /**
     * 知识库检索：按提问关键词为每条知识计分（命中的关键词个数），
     * 取分数最高的前 {@value #KB_LIMIT} 条注入提示词。
     *
     * <p>条目列表整体缓存于 Redis（10 分钟），避免每次提问都查库；
     * 知识内容变更后删除 {@code ai:kb:list} 或等待过期即可生效。</p>
     */
    private String buildKnowledgeBlock(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }
        List<AiKnowledge> matched = KnowledgeRanker.rank(question, loadKnowledge(), KB_LIMIT);
        return matched.stream()
                .map(k -> "【" + k.getTitle() + "】\n" + StrUtil.blankToDefault(k.getContent(), ""))
                .collect(Collectors.joining("\n\n"));
    }

    /** 读取启用的知识库条目（Redis 整体缓存，未命中回源数据库） */
    private List<AiKnowledge> loadKnowledge() {
        String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstants.AI_KB);
        if (StrUtil.isNotBlank(cached)) {
            try {
                List<AiKnowledge> list = JSONUtil.toList(cached, AiKnowledge.class);
                if (list != null) {
                    return list;
                }
            } catch (Exception e) {
                log.debug("[AI] 知识库缓存解析失败，回源数据库");
            }
        }
        List<AiKnowledge> list = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledge>()
                .eq(AiKnowledge::getStatus, 1)
                .orderByDesc(AiKnowledge::getPriority));
        if (list == null) {
            list = List.of();
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(RedisKeyConstants.AI_KB, JSONUtil.toJsonStr(list), KB_TTL);
        } catch (Exception e) {
            log.debug("[AI] 知识库缓存写入失败", e);
        }
        return list;
    }

    /* ==================== 数据注入 ==================== */

    /**
     * 按提问关键词选择性注入数据；所有数据均按当前用户归属过滤，不跨用户取数。
     * 管理员角色额外注入待审批 / 待处理等管理视角数据。
     */
    private String buildDataBlock(Long userId, List<String> roleCodes, String question) {
        List<String> blocks = new ArrayList<>();
        if (containsAny(question, "实验室", "空闲", "占用", "可用", "开放", "看板", "房间", "教室")) {
            blocks.add("【实验室实时占用】\n" + labBlock());
        }
        if (containsAny(question, "统计", "概览", "总数", "多少", "驾驶舱", "汇总", "数据")) {
            blocks.add("【统计概览】\n" + overviewBlock());
        }
        if (containsAny(question, "热门", "排行", "排名")) {
            blocks.add("【热门实验室 TOP5】\n" + hotLabBlock());
        }
        if (containsAny(question, "预约", "我约", "约了")) {
            blocks.add("【我的预约（最近 " + ROW_LIMIT + " 条）】\n" + myReservationBlock(userId));
        }
        if (containsAny(question, "签到", "核销", "签到码")) {
            blocks.add("【我的签到（最近 " + ROW_LIMIT + " 条）】\n" + myCheckinBlock(userId));
        }
        if (containsAny(question, "报修", "维修", "故障", "坏了", "设备")) {
            blocks.add("【当前故障设备】\n" + faultDeviceBlock());
            blocks.add("【我提交的报修（最近 " + ROW_LIMIT + " 条）】\n" + myRepairBlock(userId));
        }
        if (containsAny(question, "消息", "通知", "未读", "提醒")) {
            blocks.add("【我的消息（最近 " + ROW_LIMIT + " 条）】\n" + myNoticeBlock(userId));
        }
        if (isAdminRole(roleCodes)) {
            if (containsAny(question, "审批", "待审", "待审核", "待办")) {
                blocks.add("【待审核预约（最近 " + ROW_LIMIT + " 条）】\n" + pendingReservationBlock());
            }
            if (containsAny(question, "报修", "维修", "待处理")) {
                blocks.add("【未完成报修（最近 " + ROW_LIMIT + " 条）】\n" + pendingRepairBlock());
            }
        }
        blocks.add(slotBlock(question));
        return blocks.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining("\n\n"));
    }

    /** 超级管理员 / 实验室管理员才注入管理视角待办数据 */
    private boolean isAdminRole(List<String> roleCodes) {
        return roleCodes != null
                && (roleCodes.contains("SUPER_ADMIN") || roleCodes.contains("LAB_ADMIN"));
    }

    private boolean containsAny(String text, String... keywords) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String labBlock() {
        List<LabBoardVO> board = statisticsService.realtimeBoard();
        if (board.isEmpty()) {
            return "- 暂无实验室数据";
        }
        return board.stream().limit(ROW_LIMIT * 2).map(b -> {
            StringBuilder line = new StringBuilder()
                    .append("- ").append(b.getLabName())
                    .append("（").append(StrUtil.blankToDefault(b.getLocation(), "位置待定"))
                    .append("，容量 ").append(b.getCapacity() == null ? 0 : b.getCapacity()).append(" 人）：")
                    .append(b.getStatusDesc());
            if ("IN_USE".equals(b.getStatus()) || "RESERVED".equals(b.getStatus())) {
                line.append("，").append(StrUtil.blankToDefault(b.getCurrentUserName(), "未指定"))
                        .append(" ").append(b.getCurrentStartTime()).append("-").append(b.getCurrentEndTime());
            }
            line.append("；今日预约 ").append(b.getTodayCount() == null ? 0 : b.getTodayCount()).append(" 场");
            return line.toString();
        }).collect(Collectors.joining("\n"));
    }

    private String myReservationBlock(Long userId) {
        List<LabReservation> list = reservationMapper.selectList(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStudentId, userId)
                .orderByDesc(LabReservation::getReserveDate)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 暂无预约记录";
        }
        return list.stream().map(r -> {
            ReservationStatus status = ReservationStatus.of(r.getStatus());
            return String.format("- %s | %s | %s %s-%s | 状态：%s",
                    r.getReservationNo(), r.getLabName(), r.getReserveDate(),
                    r.getStartTime(), r.getEndTime(),
                    status == null ? r.getStatus() : status.getDesc());
        }).collect(Collectors.joining("\n"));
    }

    private String myCheckinBlock(Long userId) {
        List<LabCheckin> list = checkinMapper.selectList(new LambdaQueryWrapper<LabCheckin>()
                .eq(LabCheckin::getUserId, userId)
                .orderByDesc(LabCheckin::getReserveDate)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 暂无签到记录";
        }
        return list.stream().map(c -> {
            CheckinStatus status = CheckinStatus.of(c.getStatus());
            return String.format("- 核销码 %s | %s | %s %s-%s | 状态：%s",
                    c.getCheckinCode(), c.getLabName(), c.getReserveDate(),
                    c.getStartTime(), c.getEndTime(),
                    status == null ? c.getStatus() : status.getDesc());
        }).collect(Collectors.joining("\n"));
    }

    private String faultDeviceBlock() {
        List<Device> list = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getFaultStatus, 1)
                .orderByAsc(Device::getLabName)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 当前无故障设备";
        }
        return list.stream()
                .map(d -> String.format("- %s（%s，%s）",
                        d.getDeviceName(), d.getDeviceCode(), StrUtil.blankToDefault(d.getLabName(), "未分配实验室")))
                .collect(Collectors.joining("\n"));
    }

    private String myRepairBlock(Long userId) {
        List<DeviceRepair> list = repairMapper.selectList(new LambdaQueryWrapper<DeviceRepair>()
                .eq(DeviceRepair::getReporterId, userId)
                .orderByDesc(DeviceRepair::getCreateTime)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 暂无报修记录";
        }
        return list.stream().map(r -> {
            RepairStatus status = RepairStatus.of(r.getStatus());
            return String.format("- %s | 设备 %s | 状态：%s",
                    r.getRepairNo(), r.getDeviceName(),
                    status == null ? r.getStatus() : status.getDesc());
        }).collect(Collectors.joining("\n"));
    }

    private String overviewBlock() {
        StatsOverviewVO o = statisticsService.overview();
        BoardSummaryVO s = statisticsService.realtimeSummary();
        if (o == null && s == null) {
            return "- 暂无统计数据";
        }
        StringBuilder sb = new StringBuilder();
        if (o != null) {
            sb.append(String.format("- 实验室 %d 间、设备 %d 台、用户 %d 人、预约总次数 %d 次",
                    nvl(o.getLabTotal()), nvl(o.getDeviceTotal()),
                    nvl(o.getUserTotal()), nvl(o.getReservationTotal())));
            sb.append(String.format("%n- 今日预约 %d 次、待审核 %d 单、未完成报修 %d 单、故障设备 %d 台",
                    nvl(o.getTodayReservation()), nvl(o.getPendingApproval()),
                    nvl(o.getPendingRepair()), nvl(o.getFaultDevice())));
        }
        if (s != null) {
            sb.append(String.format("%n- 实时占用：使用中 %d、已预约 %d、空闲 %d、故障锁定 %d、未开放 %d，使用率 %.1f%%",
                    nvl(s.getInUse()), nvl(s.getReserved()), nvl(s.getIdle()),
                    nvl(s.getLocked()), nvl(s.getClosed()),
                    s.getUtilization() == null ? 0 : s.getUtilization()));
        }
        return sb.toString();
    }

    private String hotLabBlock() {
        List<HotLabVO> hot = statisticsService.hotLabs(5);
        if (hot == null || hot.isEmpty()) {
            return "- 暂无热门实验室数据";
        }
        return hot.stream()
                .map(h -> String.format("- %s：预约 %d 次",
                        h.getLabName(), h.getBookingCount() == null ? 0 : Math.round(h.getBookingCount())))
                .collect(Collectors.joining("\n"));
    }

    private String myNoticeBlock(Long userId) {
        List<SysNotice> list = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getUserId, userId)
                .orderByDesc(SysNotice::getCreateTime)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 暂无消息";
        }
        return list.stream()
                .map(n -> String.format("- %s | %s | %s",
                        n.getTitle(),
                        StrUtil.maxLength(StrUtil.blankToDefault(n.getContent(), ""), 60),
                        n.getReadFlag() != null && n.getReadFlag() == 1 ? "已读" : "未读"))
                .collect(Collectors.joining("\n"));
    }

    private String pendingReservationBlock() {
        List<LabReservation> list = reservationMapper.selectList(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStatus, ReservationStatus.PENDING.name())
                .orderByAsc(LabReservation::getCreateTime)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 当前没有待审核的预约";
        }
        return list.stream()
                .map(r -> String.format("- %s | %s 提交 | %s %s-%s | %s",
                        r.getReservationNo(), r.getStudentName(), r.getReserveDate(),
                        r.getStartTime(), r.getEndTime(), r.getLabName()))
                .collect(Collectors.joining("\n"));
    }

    private String pendingRepairBlock() {
        List<DeviceRepair> list = repairMapper.selectList(new LambdaQueryWrapper<DeviceRepair>()
                .in(DeviceRepair::getStatus,
                        RepairStatus.PENDING.name(), RepairStatus.REPAIRING.name())
                .orderByAsc(DeviceRepair::getCreateTime)
                .last("LIMIT " + ROW_LIMIT));
        if (list.isEmpty()) {
            return "- 当前没有未完成的报修";
        }
        return list.stream()
                .map(r -> {
                    RepairStatus status = RepairStatus.of(r.getStatus());
                    return String.format("- %s | 设备 %s | %s 提交 | 状态 %s | %s",
                            r.getRepairNo(), r.getDeviceName(), r.getReporterName(),
                            status == null ? r.getStatus() : status.getDesc(),
                            StrUtil.maxLength(StrUtil.blankToDefault(r.getFaultDesc(), ""), 40));
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * 提问点名了某间实验室且涉及时间/时段时，注入该实验室的开放时段配置
     */
    private String slotBlock(String question) {
        if (!containsAny(question, "时段", "几点", "时间", "开放", "能约", "可以约", "什么时候")) {
            return "";
        }
        List<LabInfo> labs = labInfoMapper.selectList(new LambdaQueryWrapper<LabInfo>()
                .eq(LabInfo::getOpenStatus, 1));
        LabInfo target = null;
        for (LabInfo lab : labs) {
            if (StrUtil.isNotBlank(lab.getLabName()) && question.contains(lab.getLabName())) {
                target = lab;
                break;
            }
        }
        if (target == null) {
            return "";
        }
        List<LabTimeSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<LabTimeSlot>()
                .eq(LabTimeSlot::getLabId, target.getId())
                .eq(LabTimeSlot::getStatus, 1)
                .orderByAsc(LabTimeSlot::getWeekDay)
                .orderByAsc(LabTimeSlot::getStartTime)
                .last("LIMIT 30"));
        if (slots.isEmpty()) {
            return "【" + target.getLabName() + " 开放时段】\n- 该实验室暂未配置开放时段";
        }
        String[] weekNames = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        StringBuilder sb = new StringBuilder("【").append(target.getLabName()).append(" 开放时段】");
        for (LabTimeSlot slot : slots) {
            int wd = slot.getWeekDay() == null ? 0 : slot.getWeekDay();
            String week = wd >= 1 && wd <= 7 ? weekNames[wd] : ("星期" + wd);
            sb.append(String.format("%n- %s %s %s-%s（人数上限 %d）",
                    week, StrUtil.blankToDefault(slot.getSlotName(), ""),
                    slot.getStartTime(), slot.getEndTime(),
                    slot.getMaxPeople() == null ? 0 : slot.getMaxPeople()));
        }
        return sb.toString();
    }

    private long nvl(Long v) {
        return v == null ? 0 : v;
    }

    /* ==================== 会话历史（Redis List） ==================== */

    private List<AiMessageVO> readHistory(Long userId) {
        List<String> raw = stringRedisTemplate.opsForList().range(RedisKeyConstants.aiChatKey(userId), 0, -1);
        List<AiMessageVO> list = new ArrayList<>();
        if (raw == null) {
            return list;
        }
        for (String json : raw) {
            try {
                AiMessageVO vo = JSONUtil.toBean(json, AiMessageVO.class);
                if (vo != null && StrUtil.isNotBlank(vo.getRole())) {
                    list.add(vo);
                }
            } catch (Exception e) {
                log.debug("[AI] 跳过无法解析的历史消息");
            }
        }
        return list;
    }

    /**
     * 追加一轮问答：push 两条 + trim 到上限 + 续期。
     * 用 List 而非"单键 JSON 读改写"，避免同用户并发时丢消息。
     */
    private void appendHistory(Long userId, String question, String answer) {
        String key = RedisKeyConstants.aiChatKey(userId);
        String now = LocalDateTime.now().format(TIME_FMT);
        stringRedisTemplate.opsForList().rightPush(key,
                JSONUtil.toJsonStr(new AiMessageVO("user", question, now)));
        stringRedisTemplate.opsForList().rightPush(key,
                JSONUtil.toJsonStr(new AiMessageVO("assistant", answer, now)));
        stringRedisTemplate.opsForList().trim(key, -aiProperties.getMaxHistory(), -1);
        stringRedisTemplate.expire(key, HISTORY_TTL);
    }
}
