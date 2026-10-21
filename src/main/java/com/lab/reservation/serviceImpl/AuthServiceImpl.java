package com.lab.reservation.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.dto.CaptchaSendDTO;
import com.lab.reservation.dto.CodeLoginDTO;
import com.lab.reservation.dto.LoginDTO;
import com.lab.reservation.dto.RegisterDTO;
import com.lab.reservation.dto.ResetPasswordDTO;
import com.lab.reservation.entity.SysUser;
import com.lab.reservation.enums.CaptchaScene;
import com.lab.reservation.enums.NoticeType;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.mapper.SysRoleMapper;
import com.lab.reservation.mapper.SysUserMapper;
import com.lab.reservation.security.JwtUtil;
import com.lab.reservation.security.LoginUser;
import com.lab.reservation.security.SecurityUtil;
import com.lab.reservation.service.AuthService;
import com.lab.reservation.service.CaptchaService;
import com.lab.reservation.service.NoticeService;
import com.lab.reservation.service.PermissionService;
import com.lab.reservation.vo.CaptchaVO;
import com.lab.reservation.vo.LoginVO;
import com.lab.reservation.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：</b>登录签发 JWT 后将 {@link LoginUser}
 * 连同其 RBAC 角色/权限写入 Redis（TTL 与令牌一致），供后续每次请求的
 * JwtAuthenticationFilter 直接命中缓存，降低数据库压力。</p>
 *
 * <p>本版本新增三条链路，均复用同一 {@link #issueToken} 签发逻辑：</p>
 * <ul>
 *   <li><b>验证码登录</b>：账号可为用户名或手机号，验证码按 userId 存储，
 *       因此用用户名取码、用手机号登录同样成立</li>
 *   <li><b>自助注册</b>：账号置为待审核、不预授任何角色，并通知管理员审核；
 *       管理员在「用户管理」通过审核时一并为其指定角色，之后账号方可登录</li>
 *   <li><b>忘记密码</b>：手机验证码校验通过后重置密码，并清除登录缓存强制重新登录</li>
 * </ul>
 *
 * @author LabWise
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CaptchaService captchaService;
    private final NoticeService noticeService;

    /** 账号状态：2 待管理员审核（自助注册的初始状态） */
    private static final int STATUS_PENDING = 2;

    /** 负责审核新注册账号的角色编码 */
    private static final String AUDITOR_ROLE_CODE = "SUPER_ADMIN";

    /** 手机号格式（与 RegisterDTO 校验保持一致） */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** 连续登录失败上限：达到后锁定一段时间，防止在线暴力破解 */
    private static final int MAX_LOGIN_FAILS = 5;

    /** 失败计数窗口同时也是锁定时长 */
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(5);

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername().trim();
        // 0. 先看是否已被锁定，避免继续消耗 BCrypt 校验
        assertNotLocked(username);
        // 1. 查询用户
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            recordLoginFail(username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        // 2. 校验密码（BCrypt 匹配）
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordLoginFail(username);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        // 3. 校验账号状态（0禁用 / 1启用 / 2待审核）
        assertLoginable(user.getStatus());
        // 4. 登录成功，清零失败计数并签发令牌
        clearLoginFail(username);
        return issueToken(user);
    }

    /**
     * 连续失败达到上限则拒绝继续尝试
     *
     * <p>计数存 Redis 并带 5 分钟 TTL：既限制单账号暴力破解，
     * 也不会因为长期未登录而误锁。用户不存在时同样计数，避免通过响应差异枚举账号。</p>
     */
    private void assertNotLocked(String username) {
        Object cached = redisTemplate.opsForValue().get(RedisKeyConstants.loginFailKey(username));
        if (cached != null && Long.parseLong(cached.toString()) >= MAX_LOGIN_FAILS) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }
    }

    private void recordLoginFail(String username) {
        String key = RedisKeyConstants.loginFailKey(username);
        Long count = redisTemplate.opsForValue().increment(key);
        // 首次计数时设置过期，窗口内持续失败则沿用原 TTL（5 分钟后自动解锁）
        if (count != null && count == 1L) {
            redisTemplate.expire(key, LOGIN_FAIL_WINDOW);
        }
    }

    private void clearLoginFail(String username) {
        redisTemplate.delete(RedisKeyConstants.loginFailKey(username));
    }

    @Override
    public CaptchaVO sendCaptcha(CaptchaSendDTO dto) {
        CaptchaScene scene = CaptchaScene.of(dto.getScene());
        if (scene == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码场景不合法");
        }
        String account = dto.getAccount().trim();
        String identity;
        if (scene == CaptchaScene.REGISTER) {
            // 注册场景用户尚不存在，直接以手机号本身为标识
            if (!account.matches(PHONE_REGEX)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "请填写正确的手机号");
            }
            identity = account;
        } else {
            // 登录 / 找回密码：先把账号解析为用户，再以 userId 为标识
            identity = String.valueOf(resolveAccount(account).getId());
        }
        return captchaService.generate(scene, identity);
    }

    @Override
    public LoginVO loginByCode(CodeLoginDTO dto) {
        SysUser user = resolveAccount(dto.getAccount());
        captchaService.verify(CaptchaScene.LOGIN, String.valueOf(user.getId()), dto.getCode());
        assertLoginable(user.getStatus());
        return issueToken(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        String phone = dto.getPhone().trim();

        // 1. 校验手机验证码
        captchaService.verify(CaptchaScene.REGISTER, phone, dto.getCode());
        // 2. 账号唯一性（库中有 uk_username，这里给出更友好的提示）
        Long sameName = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (sameName != null && sameName > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "该登录账号已被注册");
        }
        // 3. 手机号唯一性（手机号同时是登录标识）
        Long samePhone = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone));
        if (samePhone != null && samePhone > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "该手机号已被注册");
        }

        // 4. 落库：显式置为待审核（表默认值是 1，不能依赖默认值）
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(dto.getRealName().trim());
        user.setGender(0);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(STATUS_PENDING);
        sysUserMapper.insert(user);

        // 5. 注册阶段不预先授予任何角色：角色由管理员在审核环节指定，
        //    避免账号在通过审核前就带有权限（最小授权原则）
        // 6. 通知管理员审核并分配角色，闭合「注册 → 通知 → 审核分配角色 → 可登录」链路
        notifyAuditors(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        SysUser user = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, dto.getPhone().trim()))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "该手机号尚未注册"));
        captchaService.verify(CaptchaScene.RESET, String.valueOf(user.getId()), dto.getCode());
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        // 密码已变更：清除登录缓存，令旧登录态失效
        redisTemplate.delete(RedisKeyConstants.userKey(user.getId()));
    }

    @Override
    public void logout() {
        LoginUser loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null) {
            // 清除用户缓存，达到主动下线效果；后续旧 Token 因缓存缺失被拒绝
            redisTemplate.delete(RedisKeyConstants.userKey(loginUser.getUserId()));
        }
    }

    @Override
    public UserInfoVO getCurrentUserInfo() {
        return convertToUserInfo(SecurityUtil.getLoginUser());
    }

    /* ==================== 辅助逻辑 ==================== */

    /**
     * 账号解析：用户名或手机号任一命中，返回唯一用户
     *
     * <p>OR 条件必须用 {@code and(...)} 嵌套——否则 MyBatis-Plus 生成的
     * {@code deleted=0 AND username=? OR phone=?} 会因 AND 优先级高于 OR
     * 而可能命中已逻辑删除的记录。用 {@code selectList} 而非 {@code selectOne}，
     * 避免手机号重复时抛 TooManyResultsException 变成 500。</p>
     */
    private SysUser resolveAccount(String account) {
        String key = account.trim();
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .and(w -> w.eq(SysUser::getUsername, key)
                                .or().eq(SysUser::getPhone, key)))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND,
                        "账号不存在：" + key));
    }

    /**
     * 账号可登录校验：必须在签发令牌之前完成
     *
     * <p>若只拦禁用而放过待审核，会出现「令牌签发成功但每个请求都被过滤器拒绝」
     * 的中间态，前端将反复提示登录失效。</p>
     */
    private void assertLoginable(Integer status) {
        if (status == null || status == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (status == STATUS_PENDING) {
            throw new BusinessException(ResultCode.ACCOUNT_PENDING);
        }
    }

    /**
     * 签发令牌：组装 LoginUser → 生成 JWT → 写 Redis（TTL 与令牌一致）→ 组装返回体
     */
    private LoginVO issueToken(SysUser user) {
        // 重新登录即视为已确认身份，清除密码变更时写入的令牌吊销标记
        redisTemplate.delete(RedisKeyConstants.revokedKey(user.getId()));
        LoginUser loginUser = permissionService.loadLoginUserByUserId(user.getId());
        if (loginUser == null) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        String token = jwtUtil.generateToken(user.getId());
        long expireAt = jwtUtil.getExpirationDate(token).getTime();
        redisTemplate.opsForValue().set(
                RedisKeyConstants.userKey(user.getId()),
                loginUser,
                expireAt - System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType("Bearer");
        loginVO.setExpiresIn(expireAt);
        loginVO.setUserInfo(convertToUserInfo(loginUser));
        return loginVO;
    }

    /**
     * 推送「待审核」消息给负责审核的管理员
     */
    private void notifyAuditors(SysUser user) {
        List<Long> auditorIds = sysRoleMapper.selectUserIdsByRoleCode(AUDITOR_ROLE_CODE);
        if (auditorIds == null || auditorIds.isEmpty()) {
            return;
        }
        noticeService.pushAll(auditorIds, "新用户注册待审核",
                String.format("用户「%s（%s）」已提交注册申请，请前往用户管理完成审核并为其分配角色。",
                        user.getRealName(), user.getUsername()),
                NoticeType.SYSTEM, user.getId());
    }

    /**
     * LoginUser -> 对外用户信息 VO（不暴露密码等敏感字段）
     */
    private UserInfoVO convertToUserInfo(LoginUser loginUser) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setRealName(loginUser.getRealName());
        vo.setAvatar(loginUser.getAvatar());
        vo.setGender(loginUser.getGender());
        vo.setRoles(loginUser.getRoleCodes());
        vo.setPermissions(loginUser.getPermissions());
        return vo;
    }
}
