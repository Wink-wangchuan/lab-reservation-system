package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.SysUserDTO;
import com.lab.reservation.vo.SysUserVO;
import com.lab.reservation.vo.UserOptionVO;

import java.util.List;

/**
 * 系统用户管理服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface SysUserAdminService {

    /** 用户分页 */
    IPage<SysUserVO> page(IPage<SysUserVO> page, String keyword, Long roleId, Integer status);

    /** 用户详情（含角色） */
    SysUserVO detail(Long id);

    /** 新增用户（含角色分配） */
    void add(SysUserDTO dto);

    /** 更新用户（不含密码） */
    void update(SysUserDTO dto);

    /** 重置密码 */
    void resetPassword(Long id, String newPassword);

    /** 启用/禁用 */
    void toggleStatus(Long id, Integer status);

    /**
     * 审核通过（自助注册的待审核账号置为启用），并可由管理员在审核环节指定角色
     *
     * <p>注册时系统只预绑「学生」角色作为默认值，最终角色以管理员审核时提交的为准；
     * 审批后该账号必须至少拥有一个角色，否则视为无效授权并拒绝通过。</p>
     *
     * @param id      用户ID
     * @param roleIds 审核时分配的角色ID集合；为空则沿用注册时预绑的角色
     */
    void approve(Long id, List<Long> roleIds);

    /** 删除用户 */
    void delete(Long id);

    /** 为用户分配角色 */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 用户下拉选项（精简字段，仅启用用户）
     *
     * <p>供「选择实验室负责人」「选择消息接收人」等场景使用，
     * 调用方无需拥有「用户管理」权限。</p>
     */
    List<UserOptionVO> options();
}