package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.entity.SysOperLog;
import com.lab.reservation.mapper.SysOperLogMapper;
import com.lab.reservation.service.OperLogService;
import com.lab.reservation.vo.OperLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;

/**
 * 操作日志服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final SysOperLogMapper operLogMapper;

    @Override
    @Async  // 异步落库，不阻塞主业务
    public void saveLog(SysOperLog log) {
        operLogMapper.insert(log);
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