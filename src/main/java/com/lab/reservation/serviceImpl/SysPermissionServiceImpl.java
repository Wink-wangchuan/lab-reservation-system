package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.OptimisticLockGuard;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.dto.SysPermissionDTO;
import com.lab.reservation.entity.SysPermission;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysPermissionMapper;
import com.lab.reservation.service.SysPermissionService;
import com.lab.reservation.vo.PermissionNodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统权限管理服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper permissionMapper;

    @Override
    public List<PermissionNodeVO> tree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSort));
        // 构建多级树
        List<PermissionNodeVO> roots = new ArrayList<>();
        for (SysPermission p : all) {
            PermissionNodeVO node = toNode(p);
            if (p.getParentId() == null || p.getParentId() == 0) {
                roots.add(node);
            }
        }
        roots.forEach(root -> fillChildren(all, root));
        return roots;
    }

    private void fillChildren(List<SysPermission> all, PermissionNodeVO parent) {
        for (SysPermission p : all) {
            if (parent.getId().equals(p.getParentId())) {
                PermissionNodeVO child = toNode(p);
                parent.getChildren().add(child);
                fillChildren(all, child);
            }
        }
    }

    private PermissionNodeVO toNode(SysPermission p) {
        PermissionNodeVO node = new PermissionNodeVO();
        BeanUtils.copyProperties(p, node);
        return node;
    }

    @Override
    public PermissionNodeVO detail(Long id) {
        SysPermission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        return toNode(p);
    }

    @Override
    public void add(SysPermissionDTO dto) {
        SysPermission p = new SysPermission();
        BeanUtils.copyProperties(dto, p);
        if (p.getParentId() == null) {
            p.setParentId(0L);
        }
        if (p.getStatus() == null) {
            p.setStatus(1);
        }
        permissionMapper.insert(p);
    }

    @Override
    public void update(SysPermissionDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "权限ID不能为空");
        }
        SysPermission p = permissionMapper.selectById(dto.getId());
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        BeanUtils.copyProperties(dto, p);
        OptimisticLockGuard.check(permissionMapper.updateById(p), "该权限");
    }

    @Override
    public void delete(Long id) {
        if (permissionMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        Long children = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先删除子权限");
        }
        permissionMapper.deleteById(id); // 逻辑删除
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        SysPermission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态不合法");
        }
        p.setStatus(status);
        OptimisticLockGuard.check(permissionMapper.updateById(p), "该权限");
    }
}