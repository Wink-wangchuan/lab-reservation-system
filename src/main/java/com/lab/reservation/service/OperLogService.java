package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.entity.SysOperLog;
import com.lab.reservation.vo.OperLogVO;

/**
 * 操作日志服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface OperLogService {

    /** 保存操作日志（AOP 切面调用） */
    void saveLog(SysOperLog log);

    /** 日志分页查询 */
    IPage<OperLogVO> page(IPage<OperLogVO> page, String title, String operName,
                          String businessType, Integer status);
}