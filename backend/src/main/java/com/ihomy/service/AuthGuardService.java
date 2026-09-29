package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 认证防爆破:Redis 计数限流。
 * - 登录失败:账号(邮箱)与来源 IP 双计数,窗口内累加,达到上限即拒绝并持续续期;登录成功清零。
 * - 通用按 IP 限流:验证码/注册等公开接口防无限刷。
 * 计数器 TTL 固定窗口(失败即续期,被封者无法靠等待窗口内重置计数绕过)。
 */
@Service
@RequiredArgsConstructor
public class AuthGuardService {

    private final StringRedisTemplate redisTemplate;

    /** 同一账号连续失败上限 */
    private static final int MAX_FAIL_PER_ACCOUNT = 5;
    /** 同一 IP 连续失败上限(防多账号撞库) */
    private static final int MAX_FAIL_PER_IP = 20;
    /** 失败窗口/封禁时长(分钟) */
    private static final long FAIL_TTL_MINUTES = 15;

    private static final String FAIL_USER = "auth:fail:user:";
    private static final String FAIL_IP = "auth:fail:ip:";
    private static final String RATE = "auth:rate:";

    /** 登录前检查:账号或来源 IP 是否已因失败过多被封禁 */
    public void checkLoginBlocked(String ip, String email) {
        if (failCount(FAIL_USER + norm(email)) >= MAX_FAIL_PER_ACCOUNT
                || failCount(FAIL_IP + norm(ip)) >= MAX_FAIL_PER_IP) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "登录失败次数过多,请稍后再试");
        }
    }

    /** 记录一次登录失败(账号 + IP 双计数) */
    public void recordLoginFail(String ip, String email) {
        bump(FAIL_USER + norm(email), TimeUnit.MINUTES.toSeconds(FAIL_TTL_MINUTES));
        bump(FAIL_IP + norm(ip), TimeUnit.MINUTES.toSeconds(FAIL_TTL_MINUTES));
    }

    /** 登录成功:清零该账号与来源 IP 的失败计数 */
    public void clearLoginFail(String ip, String email) {
        redisTemplate.delete(FAIL_USER + norm(email));
        redisTemplate.delete(FAIL_IP + norm(ip));
    }

    /** 按 IP 限流:窗口内计数超过 max 即拒绝(固定窗口) */
    public void checkRate(String action, String ip, int max, long windowSeconds) {
        Long n = bump(RATE + action + ":" + norm(ip), windowSeconds);
        if (n > max) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "操作过于频繁,请稍后再试");
        }
    }

    private int failCount(String key) {
        String v = redisTemplate.opsForValue().get(key);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 计数 +1,首次写入时设置 TTL */
    private Long bump(String key, long ttlSeconds) {
        Long n = redisTemplate.opsForValue().increment(key);
        if (n != null && n == 1L && ttlSeconds > 0) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return n == null ? 0L : n;
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
