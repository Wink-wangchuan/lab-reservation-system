package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统权限实体
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 乐观锁版本号：管理端台账类实体启用，防止多人并发维护互相覆盖 */
    @Version
    private Integer version;

    /** 父级ID，0 为顶级 */
    private Long parentId;

    /** 权限名称 */
    private String permName;

    /** 权限标识（如 lab:reservation:submit） */
    private String permCode;

    /** 类型：1菜单 2按钮 */
    private Integer permType;

    /** 路由地址 */
    private String path;

    /** 前端组件 */
    private String component;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;
}