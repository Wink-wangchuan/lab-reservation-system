package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体（AOP 全局记录落库）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模块标题 */
    private String title;

    /** 业务类型（INSERT/UPDATE/DELETE/QUERY） */
    private String businessType;

    /** 请求方法（全限定名） */
    private String method;

    /** HTTP方法 */
    private String requestMethod;

    /** 操作人 */
    private String operName;

    /** 操作IP */
    private String operIp;

    /** 请求URL */
    private String operUrl;

    /** 请求参数（JSON） */
    private String operParam;

    /** 返回结果（截断） */
    private String jsonResult;

    /** 异常信息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作状态：0失败 1成功 */
    private Integer status;

    /** 操作时间 */
    private LocalDateTime operTime;
}