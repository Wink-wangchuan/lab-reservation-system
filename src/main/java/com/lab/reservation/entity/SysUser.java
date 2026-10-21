package com.lab.reservation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lab.reservation.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体
 *
 * @author LabWise
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 乐观锁版本号：管理端台账类实体启用，防止多人并发维护互相覆盖 */
    @Version
    private Integer version;

    /** 登录账号 */
    private String username;

    /** BCrypt 加密密码 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 性别：0男 1女 2保密 */
    private Integer gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像URL */
    private String avatar;

    /** 状态：0禁用 1启用 2待审核（自助注册的初始状态，由管理员审核放行） */
    private Integer status;

    /** 创建人 */
    private String createBy;

    /** 更新人 */
    private String updateBy;
}