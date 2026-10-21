package com.lab.reservation.service;

import com.lab.reservation.dto.SysPermissionDTO;
import com.lab.reservation.vo.PermissionNodeVO;

import java.util.List;

/**
 * 系统权限（菜单/按钮）管理服务接口
 *
 * @author LabWise
 * @since 1.0.0
 */
public interface SysPermissionService {

    /** 获取完整权限树（多级） */
    List<PermissionNodeVO> tree();

    /** 权限详情 */
    PermissionNodeVO detail(Long id);

    /** 新增权限 */
    void add(SysPermissionDTO dto);

    /** 更新权限 */
    void update(SysPermissionDTO dto);

    /** 删除权限 */
    void delete(Long id);

    /** 启用/禁用 */
    void toggleStatus(Long id, Integer status);
}
