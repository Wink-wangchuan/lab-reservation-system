package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.config.JwtProperties;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.dto.PasswordChangeDTO;
import com.lab.reservation.dto.ProfileUpdateDTO;
import com.lab.reservation.entity.DeviceRepair;
import com.lab.reservation.entity.LabReservation;
import com.lab.reservation.entity.SysNotice;
import com.lab.reservation.entity.SysRole;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.ReservationStatus;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.DeviceRepairMapper;
import com.lab.reservation.mapper.LabReservationMapper;
import com.lab.reservation.mapper.SysNoticeMapper;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.PermissionService;
import com.lab.reservation.service.ProfileService;
import com.lab.reservation.vo.ProfileStatsVO;
import com.lab.reservation.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 个人中心服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：缓存一致性维护</b></p>
 * 登录用户主体（{@link LoginUser}）整体缓存在 Redis，供 JWT 过滤器逐请求命中。
 * 个人资料变更后必须同步刷新该缓存，否则顶栏姓名/头像会在下次登录前显示为旧值——
 * 这里在刷新时沿用原缓存的剩余 TTL，保证与令牌生命周期一致。
 *
 * @author LabWise
 * @since 1.1.0
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final LabReservationMapper reservationMapper;
    private final DeviceRepairMapper repairMapper;
    private final SysNoticeMapper noticeMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 令牌有效期：密码变更后吊销标记的存活时长需覆盖旧令牌的剩余有效期 */
    private final JwtProperties jwtProperties;

    @Override
    public ProfileVO detail() {
        return convert(userMapper.selectById(SecurityUtil.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileVO update(ProfileUpdateDTO dto) {
        Long userId = SecurityUtil.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (dto.getRealName() != null && !dto.getRealName().isBlank()) {
            user.setRealName(dto.getRealName().trim());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        if (dto.getPhone() != null) {
            String phone = dto.getPhone().isBlank() ? null : dto.getPhone().trim();
            // 手机号同时是验证码登录与找回密码的标识，变更时需保证唯一
            if (phone != null && !phone.equals(user.getPhone())) {
                Long same = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, phone)
                        .ne(SysUser::getId, userId));
                if (same != null && same > 0) {
                    throw new BusinessException(ResultCode.DATA_EXISTS, "该手机号已被其他账号使用");
                }
            }
            user.setPhone(phone);
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail().isBlank() ? null : dto.getEmail().trim());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar().isBlank() ? null : dto.getAvatar().trim());
        }
        userMapper.updateById(user);
        // 资料变更后同步刷新 Redis 登录缓存
        refreshLoginCache(userId);
        return convert(userMapper.selectById(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeDTO dto) {
        Long userId = SecurityUtil.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        // 密码已变更：吊销该账号此前签发的全部令牌，避免旧令牌在有效期内继续访问
        redisTemplate.opsForValue().set(RedisKeyConstants.revokedKey(userId), 1,
                jwtProperties.getExpiration(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ProfileStatsVO stats() {
        Long userId = SecurityUtil.getUserId();
        ProfileStatsVO vo = new ProfileStatsVO();
        vo.setReservationTotal(reservationMapper.selectCount(
                new LambdaQueryWrapper<LabReservation>()
                        .eq(LabReservation::getStudentId, userId)));
        vo.setPendingReservation(reservationMapper.selectCount(
                new LambdaQueryWrapper<LabReservation>()
                        .eq(LabReservation::getStudentId, userId)
                        .eq(LabReservation::getStatus, ReservationStatus.PENDING.name())));
        vo.setApprovedReservation(reservationMapper.selectCount(
                new LambdaQueryWrapper<LabReservation>()
                        .eq(LabReservation::getStudentId, userId)
                        .eq(LabReservation::getStatus, ReservationStatus.APPROVED.name())));
        vo.setCompletedReservation(reservationMapper.selectCount(
                new LambdaQueryWrapper<LabReservation>()
                        .eq(LabReservation::getStudentId, userId)
                        .eq(LabReservation::getStatus, ReservationStatus.COMPLETED.name())));
        vo.setRepairTotal(repairMapper.selectCount(
                new LambdaQueryWrapper<DeviceRepair>()
                        .eq(DeviceRepair::getReporterId, userId)));
        vo.setUnreadNotice(noticeMapper.selectCount(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getUserId, userId)
                        .eq(SysNotice::getReadFlag, 0)));
        return vo;
    }

    /* ==================== 辅助逻辑 ==================== */

    /**
     * 重新装配登录用户主体并回写 Redis，沿用原缓存剩余 TTL
     */
    private void refreshLoginCache(Long userId) {
        String key = RedisKeyConstants.userKey(userId);
        LoginUser loginUser = permissionService.loadLoginUserByUserId(userId);
        if (loginUser == null) {
            return;
        }
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            redisTemplate.opsForValue().set(key, loginUser, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, loginUser, 2, TimeUnit.HOURS);
        }
    }

    private ProfileVO convert(SysUser user) {
        ProfileVO vo = new ProfileVO();
        if (user == null) {
            return vo;
        }
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setGender(user.getGender());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setCreateTime(user.getCreateTime());

        LoginUser loginUser = SecurityUtil.getLoginUser();
        List<String> codes = new ArrayList<>(loginUser.getRoleCodes());
        vo.setRoleCodes(codes);
        vo.setPermissionCount(loginUser.getPermissions().size());
        if (!codes.isEmpty()) {
            Map<String, String> nameMap = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                            .in(SysRole::getRoleCode, codes))
                    .stream()
                    .collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getRoleName, (a, b) -> a));
            vo.setRoleNames(codes.stream().map(c -> nameMap.getOrDefault(c, c)).toList());
        } else {
            vo.setRoleNames(List.of());
        }
        return vo;
    }
}
