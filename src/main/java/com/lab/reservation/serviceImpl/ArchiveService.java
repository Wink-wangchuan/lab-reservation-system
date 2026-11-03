package com.lab.reservation.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史数据归档
 *
 * <p>主表只保留近期数据：过期已完成/已取消/已驳回的预约移到
 * {@code lab_reservation_archive}，旧操作日志移到 {@code sys_oper_log_archive}。
 * 归档表与原表结构一致（CREATE TABLE LIKE），需要追溯时可直接查询。</p>
 *
 * <p>每月 1 日 04:00 执行，单次每表最多搬 500 条，避免长事务锁表；
 * 数据量大时会连续多个月逐步搬完，而不是一次性全表迁移。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final JdbcTemplate jdbcTemplate;

    private static final int BATCH = 500;

    @Value("${lab.archive.enabled:true}")
    private boolean enabled = true;

    @Value("${lab.archive.reservation-years:2}")
    private int reservationYears = 2;

    @Value("${lab.archive.oper-log-days:60}")
    private int operLogDays = 60;

    @Scheduled(cron = "0 0 4 1 * ?")
    @Transactional(rollbackFor = Exception.class)
    public void archiveMonthly() {
        if (!enabled) {
            return;
        }
        int reservations = archiveReservations();
        int logs = archiveOperLogs();
        if (reservations > 0 || logs > 0) {
            log.info("[归档] 本次迁移预约 {} 条、操作日志 {} 条", reservations, logs);
        }
    }

    private int archiveReservations() {
        LocalDate cutoff = LocalDate.now().minusYears(Math.max(1, reservationYears));
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM lab_reservation
                 WHERE reserve_date < ?
                   AND status IN ('COMPLETED', 'CANCELLED', 'REJECTED')
                 ORDER BY reserve_date ASC
                 LIMIT ?
                """, Long.class, cutoff, BATCH);
        if (ids.isEmpty()) {
            return 0;
        }
        String list = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("INSERT INTO lab_reservation_archive SELECT * FROM lab_reservation WHERE id IN (" + list + ")");
        jdbcTemplate.update("DELETE FROM lab_reservation WHERE id IN (" + list + ")");
        return ids.size();
    }

    private int archiveOperLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, operLogDays));
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM sys_oper_log
                 WHERE create_time < ?
                 ORDER BY create_time ASC
                 LIMIT ?
                """, Long.class, cutoff, BATCH);
        if (ids.isEmpty()) {
            return 0;
        }
        String list = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("INSERT INTO sys_oper_log_archive SELECT * FROM sys_oper_log WHERE id IN (" + list + ")");
        jdbcTemplate.update("DELETE FROM sys_oper_log WHERE id IN (" + list + ")");
        return ids.size();
    }
}
