package com.lab.reservation.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类
 *
 * <p>统一托管主键（雪花 ID）、创建/更新时间与逻辑删除字段，
 * 与 {@code MybatisMetaObjectHandler} 自动填充配合，业务实体继承即可。</p>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 逻辑删除：0 未删除 1 已删除（MyBatis-Plus 全局配置） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}