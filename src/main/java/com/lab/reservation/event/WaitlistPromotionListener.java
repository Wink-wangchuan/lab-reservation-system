package com.lab.reservation.event;

import com.lab.reservation.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 容量释放事件的候补递补监听器
 *
 * <p style="color:#c00"><b>设计要点：</b></p>
 * <ul>
 *   <li>用 {@code AFTER_COMMIT} 监听：只有取消/驳回/改期真正提交成功后才会递补，
 *       避免事务回滚了却已经把候补转成预约；</li>
 *   <li>用 {@code @Async} 异步执行：递补要锁实验室行并做容量校验，不能拖慢用户的取消操作；</li>
 *   <li>失败只记日志：候补有 5 分钟一次的兜底扫描，单次即时递补失败不会让队列卡死。</li>
 * </ul>
 *
 * @author LabWise
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistPromotionListener {

    private final WaitlistService waitlistService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatReleased(LabSeatReleasedEvent event) {
        if (event == null || event.getLabId() == null || event.getDate() == null) {
            return;
        }
        try {
            waitlistService.promoteFor(event.getLabId(), event.getDate());
        } catch (Exception e) {
            log.warn("[候补] 事件驱动递补失败，等待定时任务兜底：labId={}, date={}, reason={}",
                    event.getLabId(), event.getDate(), event.getReason(), e);
        }
    }
}
