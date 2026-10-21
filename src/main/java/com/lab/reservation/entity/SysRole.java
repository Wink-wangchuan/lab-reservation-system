package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 乐观锁版本号：管理端台账类实体启用，防止多人并发维护互相覆盖 */
    @Version
    private Integer version;

    /** 角色编码（如 TEACHER） */
    private String roleCode;

    /** 角色名称（如 教师） */
    private String roleName;

    /** 备注 */
    private String remark;

    /** 排序 */
    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;
}