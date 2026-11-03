package com.lab.reservation.service;

import java.util.List;
import java.util.Set;

/**
 * 数据权限服务：按实验室限定查询范围
 *
 * <p>约定：用户在 {@code sys_user_lab} 中存在记录 → 只能看这些实验室；
 * 没有记录 → 不限制。超级管理员始终不受限，避免误配置把自己锁死。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
public interface DataScopeService {

    /**
     * 当前登录用户可见的实验室ID集合
     *
     * @return null 表示不限制；空集合表示无任何可见实验室
     */
    Set<Long> currentVisibleLabIds();

    /** 当前登录用户是否受数据范围限制 */
    boolean currentRestricted();

    /** 查询某用户配置的实验室 */
    List<Long> labsOf(Long userId);

    /** 覆盖设置某用户的数据范围（空列表表示取消限制） */
    void assignLabs(Long userId, List<Long> labIds);
}
