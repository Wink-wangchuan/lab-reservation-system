package com.lab.reservation.enums;

import lombok.Getter;

/**
 * 操作日志业务类型（AOP 日志切面使用）
 *
 * @author LabWise
 * @since 1.0.0
 */
@Getter
public enum BusinessType {

    /** 其他 */
    OTHER("其他"),
    /** 新增 */
    INSERT("新增"),
    /** 修改 */
    UPDATE("修改"),
    /** 删除 */
    DELETE("删除"),
    /** 查询 */
    QUERY("查询"),
    /** 导出 */
    EXPORT("导出"),
    /** 审批 */
    APPROVE("审批");

    private final String desc;

    BusinessType(String desc) {
        this.desc = desc;
    }
}