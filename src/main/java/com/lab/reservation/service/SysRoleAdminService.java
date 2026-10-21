package com.lab.reservation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lab.reservation.dto.SysRoleDTO;
import com.lab.reservation.vo.SysRoleVO;

import java.util.List;

/**
 * 系统角色管理服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface SysRoleAdminService {

    /** 角色分页 */
    IPage<SysRoleVO> page(IPage<SysRoleVO> page, String keyword, Integer status);

    /** 全部启用角色（分配用） */
    List<SysRoleVO> listAll();

    /** 新增角色 */
    void add(SysRoleDTO dto);

    /** 更新角色 */
    void update(SysRoleDTO dto);

    /** 删除角色 */
    void delete(Long id);

    /** 启用/禁用角色 */
    void toggleStatus(Long id, Integer status);

    /** 为角色分配权限 */
    void assignPerms(Long roleId, List<Long> permIds);

    /** 查询角色已分配的权限ID集合 */
    List<Long> permsOf(Long roleId);
}