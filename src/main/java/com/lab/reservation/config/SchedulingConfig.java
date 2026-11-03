package com.lab.reservation.config;

import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.service.NoticeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器配置
 *
 * <p>统一设置线程池与错误处理器：任务抛异常不再只停留在控制台，
 * 而是落错误日志 + 指标计数 + 站内消息通知超级管理员，让「数据没自动推进」可被发现。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<NoticeService> noticeServiceProvider;
    private final ObjectProvider<SysRoleMapper> roleMapperProvider;

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 归档、提醒、状态推进等任务互不阻塞
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("lab-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setErrorHandler(this::onTaskError);
        return scheduler;
    }

    private void onTaskError(Throwable error) {
        log.error("[定时任务] 执行失败", error);
        try {
            MeterRegistry registry = meterRegistryProvider.getIfAvailable();
            if (registry != null) {
                Counter.builder("labwise.schedule.failures")
                        .description("定时任务执行失败次数")
                        .register(registry)
                        .increment();
            }
        } catch (Exception e) {
            log.debug("[监控] 失败计数写入异常：{}", e.getMessage());
        }
        try {
            NoticeService noticeService = noticeServiceProvider.getIfAvailable();
            SysRoleMapper roleMapper = roleMapperProvider.getIfAvailable();
            if (noticeService != null && roleMapper != null) {
                noticeService.pushAll(roleMapper.selectUserIdsByRoleCode("SUPER_ADMIN"),
                        "定时任务执行失败",
                        "有定时任务抛出异常，可能影响预约状态推进/提醒/归档，请查看服务日志。",
                        NoticeType.SYSTEM, null);
            }
        } catch (Exception e) {
            log.debug("[告警] 管理员通知发送失败：{}", e.getMessage());
        }
    }
}
