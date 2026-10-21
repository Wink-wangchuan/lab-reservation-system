package com.lab.reservation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户下拉选项 VO（精简字段）
 *
 * <p>给「实验室负责人选择」「消息接收人选择」这类只需要挑一个人的场景使用：
 * 调用方通常并不拥有「用户管理」权限（如实验室管理员、教师），
 * 因此单独提供一个字段更少、权限门槛更低的接口，避免为了一个下拉框
 * 去复用需要 {@code system:user:list} 的用户分页接口。</p>
 *
 * @author LabWise
 * @since 1.5.0
 */
@Data
@Schema(description = "用户下拉选项")
public class UserOptionVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;
}
