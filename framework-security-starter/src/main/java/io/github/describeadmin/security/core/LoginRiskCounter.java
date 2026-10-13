package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败的 IP 维度与全局维度计数，供渐进式验证码（{@link CaptchaGuard}）判断是否要求验证码。
 *
 * <p><b>为什么需要</b>：{@link LoginAttemptGuard} 只按用户名计数。密码喷洒（同一个常见密码试遍
 * 几千个用户名）与撞库（每个账号只试一次）下，每个用户名的失败数都到不了阈值，
 * 只看用户名的验证码与锁定都不会触发。IP 维度挡单一来源的喷洒，全局维度挡分散在大量 IP 上的僵尸网络。
 *
 * <p><b>固定时间窗</b>：直接复用 {@link CacheProvider#increment} 的语义——键不存在时按窗口长度设置存活时间，
 * 已存在时只自增不续期，因此一个窗口从第一次失败开始计时、到点整体清零。
 *
 * <p><b>登录成功不清零</b>（与 {@link LoginAttemptGuard#reset} 刻意不同）：否则攻击者用一个自己的
 * 有效账号定期登录一次，就能把所在 IP 乃至全局的计数刷掉。计数只随窗口自然过期。
 *
 * <p><b>只计数，不封禁</b>：达到阈值的后果是"要求验证码"，不会拒绝登录。按 IP 封禁在 NAT 后的整个
 * 办公网会误伤所有人，而验证码（默认 PoW）对正常用户几乎无感。
 */
public class LoginRiskCounter {

    private static final String IP_KEY_PREFIX = "describeadmin:login:ipfail:";
    private static final String GLOBAL_KEY = "describeadmin:login:globalfail";

    private final CacheProvider cache;
    private final Dimension ip;
    private final Dimension global;

    /**
     * @param ip     IP 维度，{@code null} 表示关闭
     * @param global 全局维度，{@code null} 表示关闭
     */
    public LoginRiskCounter(CacheProvider cache, Dimension ip, Dimension global) {
        if (cache == null) {
            throw new IllegalArgumentException("CacheProvider 不能为空");
        }
        if (ip == null && global == null) {
            throw new IllegalArgumentException("IP 维度与全局维度至少启用一个");
        }
        this.cache = cache;
        this.ip = ip;
        this.global = global;
    }

    /**
     * 记录一次登录失败。
     *
     * @param clientIp 客户端 IP，可为空——为空时只计全局维度
     */
    public void recordFailure(String clientIp) {
        if (ip != null && StringUtils.hasText(clientIp)) {
            cache.increment(IP_KEY_PREFIX + clientIp, 1L, ip.window());
        }
        if (global != null) {
            cache.increment(GLOBAL_KEY, 1L, global.window());
        }
    }

    /** IP 维度或全局维度任一达到阈值即返回 {@code true}。 */
    public boolean exceedsThreshold(String clientIp) {
        if (ip != null && StringUtils.hasText(clientIp) && ipFailures(clientIp) >= ip.threshold()) {
            return true;
        }
        return global != null && globalFailures() >= global.threshold();
    }

    /** 当前窗口内该 IP 的失败次数，只读。 */
    public long ipFailures(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return 0L;
        }
        return cache.get(IP_KEY_PREFIX + clientIp, Long.class).orElse(0L);
    }

    /** 当前窗口内全站的失败次数，只读。 */
    public long globalFailures() {
        return cache.get(GLOBAL_KEY, Long.class).orElse(0L);
    }

    /**
     * 一个计数维度：窗口内失败次数达到 {@code threshold} 即视为高风险。
     *
     * @param threshold 阈值，必须为正数
     * @param window    固定窗口长度，必须为正数
     */
    public record Dimension(int threshold, Duration window) {

        public Dimension {
            if (threshold <= 0) {
                throw new IllegalArgumentException("阈值必须为正数，当前为: " + threshold);
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("时间窗必须为正数，当前为: " + window);
            }
        }
    }
}
