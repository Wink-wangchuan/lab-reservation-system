package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.common.OptimisticLockGuard;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.config.JwtProperties;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.dto.SysUserDTO;
import com.lab.reservation.entity.DeviceRepair;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.SysRole;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.entity.SysUserRole;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.enums.RepairStatus;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceRepairMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.mapper.SysUserRoleMapper;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.SysUserAdminService;
import com.lab.reservation.vo.SysUserVO;
import com.lab.reservation.vo.UserOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统用户管理服务实现
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysUserAdminServiceImpl implements SysUserAdminService {

    /** 默认初始密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 账号状态：1 启用（审核通过后的目标状态） */
    private static final int STATUS_ENABLED = 1;

    /** 未结束的预约状态：存在这些记录的账号若被删除，预约会变成指向已删除用户的孤儿数据 */
    private static final List<String> ACTIVE_RESERVATION_STATUS = List.of(
            ReservationStatus.DRAFT.name(),
            ReservationStatus.PENDING.name(),
            ReservationStatus.APPROVED.name());

    /** 进行中的报修状态 */
    private static final List<String> ACTIVE_REPAIR_STATUS = List.of(
            RepairStatus.PENDING.name(),
            RepairStatus.REPAIRING.name());

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final LabReservationMapper reservationMapper;
    private final DeviceRepairMapper repairMapper;
    private final PasswordEncoder passwordEncoder;
    /** 账号审核/状态/密码/角色变更需告知被操作用户本人 */
    private final NoticeService noticeService;
    /** 用户状态变更后需清除其登录缓存，否则已签发的令牌在有效期内仍保持旧状态 */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 令牌有效期：吊销标记的存活时长需覆盖旧令牌的剩余有效期 */
    private final JwtProperties jwtProperties;

    @Override
    public IPage<SysUserVO> page(IPage<SysUserVO> page, String keyword, Long roleId, Integer status) {
        Long current = page.getCurrent();
        Long size = page.getSize();
        // 先分页捞用户实体
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword)
                        .or().like(SysUser::getPhone, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> entityPage = new Page<>(current, size);
        IPage<SysUser> result = userMapper.selectPage(entityPage, wrapper);

        Page<SysUserVO> voResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<SysUserVO> vos = result.getRecords().stream()
                .filter(u -> roleId == null || hasRole(u.getId(), roleId))
                .map(this::convert).toList();
        voResult.setRecords(vos);
        return voResult;
    }

    @Override
    public SysUserVO detail(Long id) {
        SysUser user = getUser(id);
        return convert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(SysUserDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setGender(dto.getGender());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        // 手机号同时是验证码登录标识，必须唯一
        assertPhoneUnique(dto.getPhone(), null);
        // 密码：优先取入参，否则默认 123456，均做 BCrypt 加密
        String raw = StrUtil.isBlank(dto.getPassword()) ? DEFAULT_PASSWORD : dto.getPassword();
        user.setPassword(passwordEncoder.encode(raw));
        userMapper.insert(user);
        // 分配角色
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            bindRoles(user.getId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserDTO dto) {
        SysUser user = getUser(dto.getId());
        user.setRealName(dto.getRealName());
        user.setGender(dto.getGender());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        assertPhoneUnique(dto.getPhone(), dto.getId());
        OptimisticLockGuard.check(userMapper.updateById(user), "该用户");
        // 若入参带角色则重绑，否则不改动
        if (dto.getRoleIds() != null) {
            bindRoles(user.getId(), dto.getRoleIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser user = getUser(id);
        String raw = StrUtil.isBlank(newPassword) ? DEFAULT_PASSWORD : newPassword;
        user.setPassword(passwordEncoder.encode(raw));
        OptimisticLockGuard.check(userMapper.updateById(user), "该用户");
        // 管理员重置密码后同样吊销该账号已签发的令牌，避免旧令牌继续可用
        revokeTokens(id);
        // 告知被重置密码的用户本人，避免其毫无察觉
        noticeService.push(id, "登录密码已被重置",
                "您的账号密码已被管理员重置，请使用新密码登录后及时修改。",
                NoticeType.SYSTEM, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        SysUser user = getUser(id);
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态不合法");
        }
        user.setStatus(status);
        OptimisticLockGuard.check(userMapper.updateById(user), "该用户");
        // 状态变更即时生效：清掉登录缓存，否则已签发令牌内仍是旧状态
        evictLoginCache(id);
        // 状态变化通知本人（禁用后需重新由管理员启用）
        noticeService.push(id,
                status == STATUS_ENABLED ? "账号已恢复启用" : "账号已被禁用",
                status == STATUS_ENABLED
                        ? "您的账号已由管理员恢复启用，现在可以正常登录使用系统。"
                        : "您的账号已由管理员禁用，如需恢复请联系系统管理员。",
                NoticeType.SYSTEM, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, List<Long> roleIds) {
        SysUser user = getUser(id);
        if (user.getStatus() != null && user.getStatus() == STATUS_ENABLED) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该账号已处于启用状态");
        }
        // 审核环节一并完成角色分配：入参带角色则以入参为准，否则沿用注册时预绑的「学生」角色
        if (roleIds != null && !roleIds.isEmpty()) {
            bindRoles(id, roleIds.stream().distinct().toList());
        }
        // 没有角色的账号登录后拿不到任何菜单与接口权限，属于无效授权，此处直接拒绝通过
        if (countUserRoles(id) == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先为该账号分配至少一个角色再通过审核");
        }
        user.setStatus(STATUS_ENABLED);
        OptimisticLockGuard.check(userMapper.updateById(user), "该用户");
        evictLoginCache(id);
        // 审核结果必须回告申请人，否则注册后只能反复试登录才能知道是否通过
        noticeService.push(id, "账号审核已通过",
                "您的注册申请已通过审核并完成角色分配，现在可以登录使用系统。",
                NoticeType.SYSTEM, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getUser(id);
        // 禁止删除当前登录账号，避免把自己锁在系统之外
        if (id.equals(SecurityUtil.getUserId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能删除当前登录的账号");
        }
        // 存在未结束的预约/报修时阻止删除，避免业务数据指向已删除用户
        Long activeReservations = reservationMapper.selectCount(new LambdaQueryWrapper<LabReservation>()
                .eq(LabReservation::getStudentId, id)
                .in(LabReservation::getStatus, ACTIVE_RESERVATION_STATUS));
        if (activeReservations != null && activeReservations > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该用户存在未结束的预约，无法删除");
        }
        Long activeRepairs = repairMapper.selectCount(new LambdaQueryWrapper<DeviceRepair>()
                .eq(DeviceRepair::getReporterId, id)
                .in(DeviceRepair::getStatus, ACTIVE_REPAIR_STATUS));
        if (activeRepairs != null && activeRepairs > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该用户存在进行中的报修单，无法删除");
        }
        // 用户采用逻辑删除（deleted=1），因此保留其角色绑定不再物理清除：
        // 既保持"主体与关系"删除语义一致，也保证账号一旦被恢复（deleted=0）
        // 角色与权限仍然完整，不会出现"能登录却没有任何权限"的错位状态。
        userMapper.deleteById(id);
        // 删除后同步清除登录缓存，否则被删账号在令牌有效期内仍可继续访问
        evictLoginCache(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        bindRoles(userId, roleIds);
        // 角色是权限的唯一来源，变更后必须清登录缓存，否则已在线账号的菜单与接口权限不会刷新
        evictLoginCache(userId);
        // 权限变化告知本人，便于其重新登录后核对可用菜单
        noticeService.push(userId, "账号角色已更新",
                "您的账号角色与权限已由管理员调整，重新登录后生效。",
                NoticeType.SYSTEM, null);
    }

    @Override
    public List<UserOptionVO> options() {
        // 仅启用用户，且只回传 id/账号/姓名，不带手机号、邮箱等联系方式
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysUser::getRealName))
                .stream().map(user -> {
                    UserOptionVO vo = new UserOptionVO();
                    vo.setId(user.getId());
                    vo.setUsername(user.getUsername());
                    vo.setRealName(user.getRealName());
                    return vo;
                }).toList();
    }

    /* ==================== 辅助逻辑 ==================== */

    private SysUser getUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 手机号唯一性校验
     *
     * <p>手机号同时是验证码登录与找回密码的身份标识，出现重复会导致账号解析歧义，
     * 因此在写入侧提前拦截（数据库另有 uk_phone 兜底）。</p>
     *
     * @param phone     待校验手机号，空值直接放行（唯一索引允许多个 NULL）
     * @param excludeId 编辑场景需排除的用户ID
     */
    private void assertPhoneUnique(String phone, Long excludeId) {
        if (StrUtil.isBlank(phone)) {
            return;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "该手机号已被其他账号使用");
        }
    }

    /**
     * 清除指定用户的登录缓存，使其状态变更即时生效
     */
    private void evictLoginCache(Long userId) {
        redisTemplate.delete(RedisKeyConstants.userKey(userId));
    }

    /**
     * 吊销指定用户已签发的全部令牌（密码被重置场景）
     * <p>JWT 无状态无法单独作废，故写入吊销标记并保留至原令牌自然过期；
     * 该用户下次登录成功后由签发逻辑清除标记</p>
     */
    private void revokeTokens(Long userId) {
        redisTemplate.opsForValue().set(RedisKeyConstants.revokedKey(userId), 1,
                jwtProperties.getExpiration(), TimeUnit.MILLISECONDS);
    }

    private boolean hasRole(Long userId, Long roleId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, roleId));
        return count != null && count > 0;
    }

    /** 统计用户当前绑定的角色数量（用于审核时校验是否已完成角色分配） */
    private long countUserRoles(Long userId) {
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        return count == null ? 0L : count;
    }

    /** 用户-角色重绑（先清后插） */
    private void bindRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        roleIds.stream().distinct().forEach(roleId -> {
            SysUserRole rel = new SysUserRole();
            rel.setUserId(userId);
            rel.setRoleId(roleId);
            userRoleMapper.insert(rel);
        });
    }

    /** 实体 -> VO（带角色信息），不暴露密码 */
    private SysUserVO convert(SysUser user) {
        SysUserVO vo = new SysUserVO();
        BeanUtils.copyProperties(user, vo);
        // 装配角色
        List<SysUserRole> rels = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, user.getId()));
        List<Long> roleIds = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        for (SysUserRole rel : rels) {
            SysRole role = roleMapper.selectById(rel.getRoleId());
            if (role != null) {
                roleIds.add(role.getId());
                roleNames.add(role.getRoleName());
            }
        }
        vo.setRoleIds(roleIds);
        vo.setRoleNames(roleNames);
        return vo;
    }
}