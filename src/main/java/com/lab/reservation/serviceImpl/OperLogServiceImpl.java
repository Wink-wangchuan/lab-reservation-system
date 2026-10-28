package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.entity.SysOperLog;
import com.lab.reservation.mapper.SysOperLogMapper;
import com.lab.reservation.service.OperLogService;
import com.lab.reservation.vo.OperLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;

/**
 * 操作日志服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final SysOperLogMapper operLogMapper;

    /** 日志保留天数（默认 90 天，可用 OPER_LOG_RETAIN_DAYS 覆盖） */
    @Value("${oper-log.retain-days:90}")
    private int retainDays;

    @Override
    @Async  // 异步落库，不阻塞主业务
    public void saveLog(SysOperLog log) {
        operLogMapper.insert(log);
    }

    /**
     * 每天凌晨清理过期操作日志
     *
     * <p>所有写操作都会落一条日志，表只增不减会持续膨胀并拖慢分页查询，
     * 因此按保留天数定期物理删除历史记录（保留天数下限 7 天，避免误配清空）。</p>
     */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeExpiredLogs() {
        int days = Math.max(7, retainDays);
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        int deleted = operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .lt(SysOperLog::getOperTime, before));
        if (deleted > 0) {
            log.info("[操作日志] 已清理 {} 天前的历史日志 {} 条", days, deleted);
        }
    }

    @Override
    public IPage<OperLogVO> page(IPage<OperLogVO> page, String title, String operName,
                                 String businessType, Integer status) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StrUtil.isNotBlank(title), SysOperLog::getTitle, title)
                .like(StrUtil.isNotBlank(operName), SysOperLog::getOperName, operName)
                .eq(StrUtil.isNotBlank(businessType), SysOperLog::getBusinessType, businessType)
                .eq(status != null, SysOperLog::getStatus, status)
                .orderByDesc(SysOperLog::getOperTime);

        Page<SysOperLog> entityPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<SysOperLog> result = operLogMapper.selectPage(entityPage, wrapper);

        Page<OperLogVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(log -> {
            OperLogVO vo = new OperLogVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }).toList());
        return voResult;
    }
}