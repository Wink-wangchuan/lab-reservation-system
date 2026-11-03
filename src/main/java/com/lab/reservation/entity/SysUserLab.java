package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户数据权限（可见实验室范围）
 *
 * <p>某个用户在表中存在记录时，其管理端查询只返回这些实验室的数据；
 * 没有记录表示不限制（保持既有管理员的全量视角）。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Data
@TableName("sys_user_lab")
public class SysUserLab implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long labId;
}
