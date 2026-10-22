package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 验证码签发频率限制：同一 IP 在固定时间窗内最多领取 {@code threshold} 道题。
 *
 * <p><b>为什么需要</b>：{@code GET /api/auth/captcha} 免登录，每调一次就往 {@link CacheProvider}
 * 写一条挑战。不设限时可以无成本地刷出大量条目——默认的内存实现容量有限、满了要淘汰，
 * 和登录锁定计数共用同一张表；即便换成 Redis 也是白白占内存。
 *
 * <p>阈值按"正常人用不到"取：登录页只在要求验证码时才领题，PoW 自动求解一次领一道。
 * 计数手法同 {@link LoginRiskCounter}：窗口从第一次领取开始计时，到点整体清零，不续期。
 * IP 同样只取 {@code request.getRemoteAddr()}（见 CLAUDE.md 4.14 第 1 条）。
 */
public class CaptchaIssueThrottle {

    private static final String KEY_PREFIX = "describeadmin:captcha:issue:";

    private final CacheProvider cache;
    private final int threshold;
    private final Duration window;

    public CaptchaIssueThrottle(CacheProvider cache, int threshold, Duration window) {
        if (cache == null) {
            throw new IllegalArgumentException("CacheProvider 不能为空");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("阈值必须为正数，当前为: " + threshold);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("时间窗必须为正数，当前为: " + window);
        }
        this.cache = cache;
        this.threshold = threshold;
        this.window = window;
    }

    /**
     * 在签发之前调用。超过阈值时抛异常，不再生成挑战、不再写缓存。
     *
     * @param clientIp 客户端 IP，为空时不计数（拿不到地址时宁可放行，也不要把所有人算成同一个来源）
     */
    public void acquire(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        if (cache.increment(KEY_PREFIX + clientIp, 1L, window) > threshold) {
            throw new BizException(ResultCode.BAD_REQUEST, "获取验证码过于频繁，请稍后再试");
        }
    }
}
