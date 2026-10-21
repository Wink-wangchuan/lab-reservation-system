package com.lab.reservation.serviceImpl;

import cn.hutool.core.util.StrUtil;
import com.lab.reservation.common.ResultCode;
import com.lab.reservation.constant.RedisKeyConstants;
import com.lab.reservation.enums.CaptchaScene;
import com.lab.reservation.exception.BusinessException;
import com.lab.reservation.service.CaptchaService;
import com.lab.reservation.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务实现
 *
 * <p style="color:#c00"><b>答辩亮点：Redis 承载的验证码一次性凭证</b></p>
 * <ul>
 *   <li>6 位数字随机码，写入 Redis 并带 5 分钟 TTL</li>
 *   <li>独立的 60 秒节流键（{@code setIfAbsent} 原子占位），防止连点刷码</li>
 *   <li>校验采用「先比对、再删除，并以删除结果作为通过闸门」，
 *       避免并发下同一验证码被重复消费</li>
 * </ul>
 *
 * <p>存储使用 {@link StringRedisTemplate} 而非带 JSON 类型标记的 RedisTemplate，
 * 与统计模块的缓存写法保持一致。</p>
 *
 * @author LabWise
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否在响应中回传验证码本身
     *
     * <p><b>默认关闭</b>：验证码是登录凭证，一旦随响应返回，任何人只要知道用户名
     * 就能匿名取码并直接登录该账号（等同于认证绕过）。仅在未接入短信通道的
     * 演示环境通过 {@code CAPTCHA_EXPOSE_CODE=true} 临时开启。</p>
     */
    @Value("${auth.captcha.expose-code:false}")
    private boolean exposeCode;

    /** 验证码有效期 */
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    /** 同一标识的发送节流窗口 */
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    @Override
    public CaptchaVO generate(CaptchaScene scene, String identity) {
        // 1. 节流：setIfAbsent 原子占位，成功才允许发送
        String cooldownKey = RedisKeyConstants.captchaCooldownKey(scene.getPrefix(), identity);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", COOLDOWN_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ResultCode.CAPTCHA_TOO_FREQUENT);
        }

        // 2. 随机 6 位数字，写入即带 TTL（避免 set 之后再 expire 的竞态）
        String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        stringRedisTemplate.opsForValue()
                .set(RedisKeyConstants.captchaKey(scene.getPrefix(), identity), code, CAPTCHA_TTL);

        CaptchaVO vo = new CaptchaVO();
        // 仅演示环境回传验证码；关闭时前端只提示「已发送」
        if (exposeCode) {
            vo.setCode(code);
        } else {
            log.info("[验证码] 场景={} 标识={} 已生成，未回传（生产应由短信通道下发）",
                    scene.getPrefix(), identity);
        }
        vo.setExpireSeconds((int) CAPTCHA_TTL.toSeconds());
        return vo;
    }

    @Override
    public void verify(CaptchaScene scene, String identity, String code) {
        String key = RedisKeyConstants.captchaKey(scene.getPrefix(), identity);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(cached)) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }
        if (!cached.equals(code.trim())) {
            throw new BusinessException(ResultCode.CAPTCHA_INVALID);
        }
        // 比对通过后再消费；删除失败说明已被并发使用，同样拒绝
        Boolean removed = stringRedisTemplate.delete(key);
        if (!Boolean.TRUE.equals(removed)) {
            throw new BusinessException(ResultCode.CAPTCHA_INVALID);
        }
    }
}
