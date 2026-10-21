package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.OptimisticLockGuard;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.SysRoleDTO;
import com.lab.reservation.entity.SysRole;
import com.lab.reservation.entity.SysRolePermission;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysRolePermissionMapper;
import com.lab.reservation.service.SysRoleAdminService;
import com.lab.reservation.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import java.util.List;

/**
 * 系统角色管理服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysRoleAdminServiceImpl implements SysRoleAdminService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    public IPage<SysRoleVO> page(IPage<SysRoleVO> page, String keyword, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysRole::getRoleName, keyword)
                        .or().like(SysRole::getRoleCode, keyword))
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getSort)
                .orderByDesc(SysRole::getCreateTime);
        var result = roleMapper.selectPage(
                new Page<>(page.getCurrent(), page.getSize()), wrapper);
        Page<SysRoleVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voResult.setRecords(result.getRecords().stream().map(this::convert).toList());
        return voResult;
    }

    @Override
    public List<SysRoleVO> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSort))
                .stream().map(this::convert).toList();
    }

    private SysRoleVO convert(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        BeanUtils.copyProperties(role, vo);
        vo.setPermIds(permsOf(role.getId()));
        return vo;
    }

    @Override
    public void add(SysRoleDTO dto) {
        checkUnique(dto.getRoleCode(), null);
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        role.setId(null);
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleMapper.insert(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRoleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(dto.getId());
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        checkUnique(dto.getRoleCode(), dto.getId());
        BeanUtils.copyProperties(dto, role);
        OptimisticLockGuard.check(roleMapper.updateById(role), "该角色");
        // 若带权限则重绑
        if (dto.getPermIds() != null) {
            bindPerms(role.getId(), dto.getPermIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (roleMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态不合法");
        }
        role.setStatus(status);
        OptimisticLockGuard.check(roleMapper.updateById(role), "该角色");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPerms(Long roleId, List<Long> permIds) {
        bindPerms(roleId, permIds);
    }

    @Override
    public List<Long> permsOf(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }

    private void checkUnique(String roleCode, Long excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "角色编码已存在");
        }
    }

    /** 角色-权限重绑（先清后插） */
    private void bindPerms(Long roleId, List<Long> permIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        permIds.stream().distinct().forEach(pid -> {
            SysRolePermission rel = new SysRolePermission();
            rel.setRoleId(roleId);
            rel.setPermissionId(pid);
            rolePermissionMapper.insert(rel);
        });
    }
}