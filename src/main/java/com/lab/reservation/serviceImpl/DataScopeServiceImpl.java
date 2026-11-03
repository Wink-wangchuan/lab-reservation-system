package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.entity.SysUserLab;
import com.lab.reservation.mapper.SysUserLabMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限实现
 *
 * @author LabWise
 * @since 4.6.0
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private final SysUserLabMapper userLabMapper;

    @Override
    public Set<Long> currentVisibleLabIds() {
        LoginUser user = SecurityUtil.getLoginUser();
        if (user == null) {
            return null;
        }
        // 超级管理员不受数据范围限制
        if (user.getRoleCodes() != null && user.getRoleCodes().contains("SUPER_ADMIN")) {
            return null;
        }
        List<Long> labs = labsOf(user.getUserId());
        return labs.isEmpty() ? null : new LinkedHashSet<>(labs);
    }

    @Override
    public boolean currentRestricted() {
        return currentVisibleLabIds() != null;
    }

    @Override
    public List<Long> labsOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userLabMapper.selectList(new LambdaQueryWrapper<SysUserLab>()
                        .eq(SysUserLab::getUserId, userId))
                .stream().map(SysUserLab::getLabId).distinct().toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignLabs(Long userId, List<Long> labIds) {
        // 全量覆盖：先清空再写入，语义简单且避免重复行
        userLabMapper.delete(new LambdaQueryWrapper<SysUserLab>()
                .eq(SysUserLab::getUserId, userId));
        if (labIds == null || labIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = labIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long labId : distinct) {
            SysUserLab row = new SysUserLab();
            row.setUserId(userId);
            row.setLabId(labId);
            userLabMapper.insert(row);
        }
    }
}
