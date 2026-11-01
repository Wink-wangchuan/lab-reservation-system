package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.entity.Device;
import com.lab.reservation.entity.LabInfo;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceMapper;
import com.lab.reservation.mapper.LabInfoMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.StatsMapper;
import com.lab.reservation.service.StatisticsService;
import com.lab.reservation.vo.BoardSummaryVO;
import com.lab.reservation.vo.HotLabVO;
import com.lab.reservation.vo.LabBoardVO;
import com.lab.reservation.vo.NameValueVO;
import com.lab.reservation.vo.StatsMetricsVO;
import com.lab.reservation.vo.StatsOverviewVO;
import com.lab.reservation.vo.TrendPointVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 数据统计分析服务实现
 *
 * <p>统计结果统一缓存于 Redis：缓存键内嵌版本号，写操作（预约/报修等）自增版本号
 * 即可让全部统计缓存整体失效，无需 KEYS 扫描；TTL 仅作兜底。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 统计结果缓存有效期（写操作会主动失效，TTL 仅作兜底） */
    private static final Duration STAT_TTL = Duration.ofMinutes(5);

    /** 实时占用看板缓存有效期（短 TTL，保证"实时"观感） */
    private static final Duration BOARD_TTL = Duration.ofSeconds(20);

    /** 看板预约数据窗口：今日起往后 7 天，避免「有已通过预约却看不到」的错觉 */
    private static final int BOARD_WINDOW_DAYS = 7;

    private static final TypeReference<StatsOverviewVO> OVERVIEW_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<NameValueVO>> NAME_VALUE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<TrendPointVO>> TREND_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LabBoardVO>> BOARD_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<StatsMetricsVO> METRICS_TYPE = new TypeReference<>() {
    };

    private final LabInfoMapper labInfoMapper;
    private final DeviceMapper deviceMapper;
    private final LabReservationMapper reservationMapper;
    private final StatsMapper statsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final List<String> INVALID_STATUS =
            List.of(ReservationStatus.CANCELLED.name(), ReservationStatus.REJECTED.name());

    @Override
    public void recordReservationBooking(Long labId, String labName) {
        // 缓存属于加速层：Redis 不可用时不能让预约提交失败，记录告警后继续走数据库统计
        try {
            // ZSet: 以实验室名称为成员，score 累加预约热度
            stringRedisTemplate.opsForZSet()
                    .incrementScore(RedisKeyConstants.HOT_LAB_RANK, labName, 1);
        } catch (Exception e) {
            log.warn("[统计] 热门实验室热度累加失败，已降级：{}", e.getMessage());
        }
        // 统计缓存整体失效，下次请求重算
        evictStatCache();
    }

    @Override
    public void evictStatCache() {
        try {
            // 版本号自增：旧缓存键立即失效，新请求落到新版本键，旧键由 TTL 自然回收
            stringRedisTemplate.opsForValue().increment(RedisKeyConstants.STAT_CACHE_VERSION);
        } catch (Exception e) {
            log.warn("[统计] 缓存版本自增失败，已降级：{}", e.getMessage());
        }
    }

    @Override
    public List<HotLabVO> hotLabs(int topN) {
        try {
            // 1. 优先读 Redis ZSet 倒序 TopN
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    stringRedisTemplate.opsForZSet()
                            .reverseRangeWithScores(RedisKeyConstants.HOT_LAB_RANK, 0, topN - 1L);
            if (tuples != null && !tuples.isEmpty()) {
                return tuples.stream().map(t -> {
                    HotLabVO vo = new HotLabVO();
                    vo.setLabName(t.getValue());
                    vo.setBookingCount(t.getScore());
                    return vo;
                }).toList();
            }
        } catch (Exception e) {
            log.warn("[统计] 热门实验室缓存读取失败，改为从数据库重建：{}", e.getMessage());
        }
        // 2. 兜底：从库按预约记录分组重建排行
        return rebuildHotLabs(topN);
    }

    /**
     * 兜底重建：从预约表按实验室分组统计（Redis 无数据时用，答辩演示反查路径）
     */
    private List<HotLabVO> rebuildHotLabs(int topN) {
        QueryWrapper<LabReservation> wrapper = new QueryWrapper<LabReservation>()
                .select("lab_id AS labId", "lab_name AS labName", "COUNT(*) AS cnt")
                .notIn("status", INVALID_STATUS)
                .groupBy("lab_id", "lab_name")
                .orderByDesc("cnt")
                .last("LIMIT " + topN);
        List<Map<String, Object>> maps = reservationMapper.selectMaps(wrapper);
        List<HotLabVO> list = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            HotLabVO vo = new HotLabVO();
            vo.setLabId(Long.valueOf(m.get("labId").toString()));
            vo.setLabName(String.valueOf(m.get("labName")));
            vo.setBookingCount(((Number) m.get("cnt")).doubleValue());
            list.add(vo);
        }
        // 重建后回填 Redis（供后续直接命中）；回填失败不影响本次返回
        if (!list.isEmpty()) {
            try {
                stringRedisTemplate.delete(RedisKeyConstants.HOT_LAB_RANK);
                list.forEach(v -> stringRedisTemplate.opsForZSet()
                        .add(RedisKeyConstants.HOT_LAB_RANK, v.getLabName(), v.getBookingCount()));
            } catch (Exception e) {
                log.warn("[统计] 热门实验室回填失败，已降级：{}", e.getMessage());
            }
        }
        return list;
    }

    @Override
    public StatsOverviewVO overview() {
        return cached("overview", OVERVIEW_TYPE, this::loadOverview);
    }

    @Override
    public List<NameValueVO> reservationStatusDistribution() {
        return cached("status", NAME_VALUE_LIST_TYPE, this::loadStatusDistribution);
    }

    @Override
    public List<TrendPointVO> reservationTrend(int days) {
        return cached("trend:" + days, TREND_LIST_TYPE, () -> loadTrend(days));
    }

    @Override
    public List<NameValueVO> labTypeDistribution() {
        return cached("labtype", NAME_VALUE_LIST_TYPE, this::loadLabTypeDistribution);
    }

    @Override
    public List<LabBoardVO> realtimeBoard() {
        String version = currentVersion();
        String key = RedisKeyConstants.boardRealtimeKey(version);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                try {
                    return objectMapper.readValue(json, BOARD_LIST_TYPE);
                } catch (JsonProcessingException ignored) {
                    // 缓存内容异常时按未命中处理
                }
            }
        } catch (Exception e) {
            log.warn("[统计] 实时看板缓存读取失败，直接回源：{}", e.getMessage());
        }
        List<LabBoardVO> list = loadBoard();
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, objectMapper.writeValueAsString(list), BOARD_TTL);
        } catch (Exception ignored) {
            // 写缓存失败（含 Redis 不可用）不影响业务返回
        }
        return list;
    }

    @Override
    public BoardSummaryVO realtimeSummary() {
        List<LabBoardVO> board = realtimeBoard();
        BoardSummaryVO vo = new BoardSummaryVO();
        long inUse = board.stream().filter(b -> "IN_USE".equals(b.getStatus())).count();
        vo.setLabTotal((long) board.size());
        vo.setInUse(inUse);
        vo.setReserved(board.stream().filter(b -> "RESERVED".equals(b.getStatus())).count());
        vo.setIdle(board.stream().filter(b -> "IDLE".equals(b.getStatus())).count());
        vo.setLocked(board.stream().filter(b -> "LOCKED".equals(b.getStatus())).count());
        vo.setClosed(board.stream().filter(b -> "CLOSED".equals(b.getStatus())).count());
        // 使用率 = 使用中 / 可开放实验室（剔除锁定与未开放）
        long available = board.size() - vo.getLocked() - vo.getClosed();
        vo.setUtilization(available <= 0 ? 0.0
                : Math.round(inUse * 1000.0 / available) / 10.0);
        return vo;
    }

    /**
     * 实时占用看板回源计算。
     *
     * <p>占用状态（使用中/已预约/空闲）严格按「今天」的预约时段推导——
     * 未来日期的已通过预约只作为「下次预约」提示返回，不能把今天的实验室误判为非空闲；
     * 数据窗口仍保留 {@link #BOARD_WINDOW_DAYS} 天，用于计算该提示。
     * 状态优先级：未开放 &gt; 故障锁定 &gt; 使用中 &gt; 已预约 &gt; 空闲。</p>
     */
    private List<LabBoardVO> loadBoard() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(BOARD_WINDOW_DAYS);
        LocalTime now = LocalTime.now();
        List<LabInfo> labs = labInfoMapper.selectList(new LambdaQueryWrapper<LabInfo>()
                .orderByAsc(LabInfo::getLabCode));
        List<LabReservation> windowReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<LabReservation>()
                        .ge(LabReservation::getReserveDate, today)
                        .le(LabReservation::getReserveDate, windowEnd)
                        .in(LabReservation::getStatus,
                                List.of(ReservationStatus.APPROVED.name(),
                                        ReservationStatus.PENDING.name())));
        Map<Long, List<LabReservation>> byLab = windowReservations.stream()
                .collect(Collectors.groupingBy(LabReservation::getLabId));

        // 一次查出全部故障设备所在实验室，避免在下面的循环里逐间实验室查一次（N+1）
        Set<Long> lockedLabIds = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .select(Device::getLabId)
                        .eq(Device::getFaultStatus, 1))
                .stream()
                .map(Device::getLabId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<LabBoardVO> list = new ArrayList<>();
        for (LabInfo lab : labs) {
            List<LabReservation> items = byLab.getOrDefault(lab.getId(), List.of());
            List<LabReservation> approved = items.stream()
                    .filter(r -> ReservationStatus.APPROVED.name().equals(r.getStatus()))
                    .toList();
            // 占用状态只看「今天」：未来日期的预约不得把今天的实验室算成非空闲
            List<LabReservation> approvedToday = approved.stream()
                    .filter(r -> today.equals(r.getReserveDate()))
                    .toList();
            LabBoardVO vo = new LabBoardVO();
            vo.setLabId(lab.getId());
            vo.setLabCode(lab.getLabCode());
            vo.setLabName(lab.getLabName());
            vo.setLabType(lab.getLabType());
            vo.setLocation(lab.getLocation());
            vo.setCapacity(lab.getCapacity());
            vo.setTodayCount((long) approvedToday.size());

            boolean closed = lab.getOpenStatus() == null || lab.getOpenStatus() != 1
                    || lab.getEnableReserve() == null || lab.getEnableReserve() != 1;
            boolean locked = lockedLabIds.contains(lab.getId());
            // 使用中：今日已通过、且当前时刻落在时段内
            LabReservation current = approvedToday.stream()
                    .filter(r -> !r.getStartTime().isAfter(now) && r.getEndTime().isAfter(now))
                    .findFirst().orElse(null);
            // 今日后续待开始（不含未来日期）
            vo.setUpcomingCount(approvedToday.stream()
                    .filter(r -> r.getStartTime().isAfter(now))
                    .count());
            // 已预约：今日尚未结束的最近一条已通过预约
            LabReservation nextToday = approvedToday.stream()
                    .filter(r -> r.getEndTime().isAfter(now))
                    .min(Comparator.comparing(LabReservation::getStartTime))
                    .orElse(null);
            // 今日待审核的预约同样按「已预约」占位展示
            LabReservation pendingToday = items.stream()
                    .filter(r -> today.equals(r.getReserveDate()))
                    .filter(r -> ReservationStatus.PENDING.name().equals(r.getStatus()))
                    .findFirst().orElse(null);
            // 下一次（未来日期）的已通过预约：只作为提示信息，不参与今天的占用状态
            List<LabReservation> future = approved.stream()
                    .filter(r -> r.getReserveDate().isAfter(today))
                    .toList();
            vo.setFutureCount((long) future.size());
            LabReservation nextFuture = future.stream()
                    .min(Comparator.comparing(LabReservation::getReserveDate)
                            .thenComparing(LabReservation::getStartTime))
                    .orElse(null);
            if (nextFuture != null) {
                vo.setNextReserveDate(nextFuture.getReserveDate());
                vo.setNextStartTime(nextFuture.getStartTime());
                vo.setNextEndTime(nextFuture.getEndTime());
            }

            if (closed) {
                mark(vo, "CLOSED");
            } else if (locked) {
                mark(vo, "LOCKED");
            } else if (current != null) {
                mark(vo, "IN_USE");
                fillCurrent(vo, current);
            } else if (nextToday != null || pendingToday != null) {
                mark(vo, "RESERVED");
                fillCurrent(vo, nextToday != null ? nextToday : pendingToday);
            } else {
                mark(vo, "IDLE");
            }
            list.add(vo);
        }
        return list;
    }

    private void mark(LabBoardVO vo, String status) {
        vo.setStatus(status);
        vo.setStatusDesc(switch (status) {
            case "IN_USE" -> "使用中";
            case "RESERVED" -> "已预约";
            case "LOCKED" -> "故障锁定";
            case "CLOSED" -> "未开放";
            default -> "空闲";
        });
    }

    private void fillCurrent(LabBoardVO vo, LabReservation reservation) {
        vo.setCurrentReservationNo(reservation.getReservationNo());
        vo.setCurrentUserName(reservation.getStudentName());
        vo.setCurrentReserveDate(reservation.getReserveDate());
        vo.setCurrentStartTime(reservation.getStartTime());
        vo.setCurrentEndTime(reservation.getEndTime());
        vo.setPurpose(reservation.getPurpose());
    }

    @Override
    public StatsMetricsVO quality() {
        return cached("quality", METRICS_TYPE, this::loadQuality);
    }

    /**
     * 使用率报表导出：三页工作表一次性输出，供教务/实验室管理做周期复盘
     *
     * <p>与预约明细导出同样使用 SXSSF 流式写盘；报表本身是聚合结果，行数很少，
     * 但这套写法可以避免以后改成「明细 + 聚合」混合报表时再改结构。</p>
     */
    @Override
    public void exportUsageExcel(HttpServletResponse response, int days) {
        int span = Math.min(Math.max(days, 1), 365);
        LocalDate start = LocalDate.now().minusDays(span - 1L);
        String period = start + " ~ " + LocalDate.now();

        List<Map<String, Object>> labUsage = statsMapper.selectLabUsage(start);
        List<Map<String, Object>> slotUsage = statsMapper.selectSlotUsage(start);
        List<Map<String, Object>> teacherUsage = statsMapper.selectTeacherUsage(start);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            CellStyle headerStyle = headerStyle(workbook);
            buildLabUsageSheet(workbook, headerStyle, labUsage, period);
            buildSlotUsageSheet(workbook, headerStyle, slotUsage, period);
            buildTeacherUsageSheet(workbook, headerStyle, teacherUsage, period);

            response.setContentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = new String(
                    ("使用率报表_" + span + "天_" + LocalDate.now() + ".xlsx")
                            .getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.ISO_8859_1);
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.EXPORT_ERROR, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 履约质量回源：比率在 Java 侧计算，SQL 只负责取计数，避免除零与精度问题散落在 SQL 里
     */
    private StatsMetricsVO loadQuality() {
        StatsMetricsVO vo = statsMapper.selectQuality();
        if (vo == null) {
            vo = new StatsMetricsVO();
        }
        long approved = vo.getApprovedTotal() == null ? 0 : vo.getApprovedTotal();
        long rejected = vo.getRejectedTotal() == null ? 0 : vo.getRejectedTotal();
        long noShow = vo.getNoShowTotal() == null ? 0 : vo.getNoShowTotal();
        long checkin = vo.getCheckinTotal() == null ? 0 : vo.getCheckinTotal();
        long reviewed = approved + rejected;
        vo.setApprovalRate(rate(approved, reviewed));
        vo.setRejectRate(rate(rejected, reviewed));
        vo.setNoShowRate(rate(noShow, checkin));
        vo.setAvgApprovalMinutes(vo.getAvgApprovalMinutes() == null ? 0.0 : vo.getAvgApprovalMinutes());
        return vo;
    }

    /** 百分比保留一位小数，分母为 0 时返回 0 */
    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    /* ==================== 使用率报表工作表 ==================== */

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void buildLabUsageSheet(SXSSFWorkbook workbook, CellStyle style,
                                    List<Map<String, Object>> rows, String period) {
        Sheet sheet = workbook.createSheet("实验室使用率");
        String[] headers = {"实验室", "有效预约场次", "累计使用人数", "累计使用时长(小时)", "场均人数"};
        writeHeader(sheet, headers, new int[]{24, 14, 16, 20, 12}, style);
        int index = 1;
        for (Map<String, Object> row : rows) {
            long total = longOf(row.get("total"));
            long minutes = longOf(row.get("minutes"));
            long people = longOf(row.get("people"));
            Row excelRow = sheet.createRow(index++);
            excelRow.createCell(0).setCellValue(stringOf(row.get("labName")));
            excelRow.createCell(1).setCellValue(total);
            excelRow.createCell(2).setCellValue(people);
            excelRow.createCell(3).setCellValue(Math.round(minutes / 6.0) / 10.0);
            excelRow.createCell(4).setCellValue(total == 0 ? 0 : Math.round(people * 10.0 / total) / 10.0);
        }
        appendNote(sheet, index, "统计区间：" + period);
    }

    private void buildSlotUsageSheet(SXSSFWorkbook workbook, CellStyle style,
                                     List<Map<String, Object>> rows, String period) {
        Sheet sheet = workbook.createSheet("时段热度");
        String[] headers = {"时段", "有效预约场次", "累计使用人数"};
        writeHeader(sheet, headers, new int[]{20, 16, 16}, style);
        int index = 1;
        for (Map<String, Object> row : rows) {
            Row excelRow = sheet.createRow(index++);
            excelRow.createCell(0).setCellValue(
                    stringOf(row.get("startTime")) + " - " + stringOf(row.get("endTime")));
            excelRow.createCell(1).setCellValue(longOf(row.get("total")));
            excelRow.createCell(2).setCellValue(longOf(row.get("people")));
        }
        appendNote(sheet, index, "统计区间：" + period);
    }

    private void buildTeacherUsageSheet(SXSSFWorkbook workbook, CellStyle style,
                                        List<Map<String, Object>> rows, String period) {
        Sheet sheet = workbook.createSheet("审批绩效");
        String[] headers = {"审批人", "通过数", "驳回数", "处理总数", "平均响应时长(分钟)"};
        writeHeader(sheet, headers, new int[]{16, 12, 12, 14, 20}, style);
        int index = 1;
        for (Map<String, Object> row : rows) {
            Row excelRow = sheet.createRow(index++);
            excelRow.createCell(0).setCellValue(stringOf(row.get("teacherName")));
            excelRow.createCell(1).setCellValue(longOf(row.get("approved")));
            excelRow.createCell(2).setCellValue(longOf(row.get("rejected")));
            excelRow.createCell(3).setCellValue(longOf(row.get("total")));
            excelRow.createCell(4).setCellValue(numberOf(row.get("avgMinutes")));
        }
        appendNote(sheet, index, "统计区间：" + period);
    }

    private void writeHeader(Sheet sheet, String[] headers, int[] widths, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            // 固定列宽：SXSSF 下 autoSizeColumn 需要缓存全部行，会抵消流式写入优势
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void appendNote(Sheet sheet, int rowIndex, String note) {
        Row row = sheet.createRow(rowIndex + 1);
        row.createCell(0).setCellValue(note);
    }

    private long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double numberOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ==================== 缓存读写 ====================

    /**
     * 读穿透缓存：命中则反序列化返回；未命中回源并写回。
     * 缓存读/写异常一律降级为直接回源，不影响业务。
     */
    private <T> T cached(String biz, TypeReference<T> type, Supplier<T> loader) {
        String key = statCacheKey(biz);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                try {
                    return objectMapper.readValue(json, type);
                } catch (JsonProcessingException ignored) {
                    // 缓存内容异常（如结构变更）时按未命中处理
                }
            }
        } catch (Exception e) {
            log.warn("[统计] 缓存读取失败（{}），直接回源：{}", biz, e.getMessage());
        }
        T value = loader.get();
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, objectMapper.writeValueAsString(value), STAT_TTL);
        } catch (Exception ignored) {
            // 写缓存失败（含 Redis 不可用）不影响业务返回
        }
        return value;
    }

    /** 组装带当前缓存版本的键 */
    private String statCacheKey(String biz) {
        return RedisKeyConstants.statCacheKey(biz, currentVersion());
    }

    /** 当前缓存版本号（统计与实时看板共用同一版本计数器）；Redis 不可用时按 0 处理 */
    private String currentVersion() {
        try {
            String version = stringRedisTemplate.opsForValue()
                    .get(RedisKeyConstants.STAT_CACHE_VERSION);
            return StrUtil.blankToDefault(version, "0");
        } catch (Exception e) {
            log.warn("[统计] 缓存版本读取失败，按 0 处理：{}", e.getMessage());
            return "0";
        }
    }

    // ==================== 各统计项回源实现 ====================

    private StatsOverviewVO loadOverview() {
        // 8 项指标合并为一次 SQL：原实现分别 count 8 次，缓存失效时要经历 8 个数据库往返
        StatsOverviewVO vo = statsMapper.selectOverview();
        return vo == null ? new StatsOverviewVO() : vo;
    }

    private List<NameValueVO> loadStatusDistribution() {
        QueryWrapper<LabReservation> wrapper = new QueryWrapper<LabReservation>()
                .select("status", "COUNT(*) AS cnt")
                .groupBy("status");
        List<Map<String, Object>> maps = reservationMapper.selectMaps(wrapper);
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            String status = String.valueOf(m.get("status"));
            ReservationStatus statusEnum = ReservationStatus.of(status);
            String name = statusEnum == null ? status : statusEnum.getDesc();
            list.add(new NameValueVO(name, Long.valueOf(m.get("cnt").toString())));
        }
        return list;
    }

    private List<TrendPointVO> loadTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        // 一次分组查询：按预约日期统计有效预约数，避免逐天 count 的 N 次查询
        QueryWrapper<LabReservation> wrapper = new QueryWrapper<LabReservation>()
                .select("DATE_FORMAT(reserve_date, '%Y-%m-%d') AS reserveDay", "COUNT(*) AS cnt")
                .between("reserve_date", startDate, today)
                .notIn("status", INVALID_STATUS)
                .groupBy("reserve_date");
        List<Map<String, Object>> maps = reservationMapper.selectMaps(wrapper);
        Map<String, Long> countByDay = new HashMap<>();
        for (Map<String, Object> m : maps) {
            countByDay.put(String.valueOf(m.get("reserveDay")),
                    ((Number) m.get("cnt")).longValue());
        }
        // 按日期范围补齐无数据日期为 0，保持原顺序（由早到晚）与返回结构
        List<TrendPointVO> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.format(DAY);
            TrendPointVO vo = new TrendPointVO();
            vo.setDate(key);
            vo.setCount(countByDay.getOrDefault(key, 0L));
            list.add(vo);
        }
        return list;
    }

    private List<NameValueVO> loadLabTypeDistribution() {
        QueryWrapper<LabInfo> wrapper = new QueryWrapper<LabInfo>()
                .select("lab_type AS name", "COUNT(*) AS value")
                .groupBy("lab_type");
        List<Map<String, Object>> maps = labInfoMapper.selectMaps(wrapper);
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            list.add(new NameValueVO(
                    String.valueOf(m.get("name")),
                    Long.valueOf(m.get("value").toString())));
        }
        return list;
    }
}
