package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 登录失败次数限制。
 *
 * <p>在锁定窗口内连续失败达到阈值后，暂时拒绝该账号的登录尝试，
 * 使在线爆破从"受限于网络吞吐"变成"受限于锁定窗口"。
 *
 * <p><b>已知取舍：可被用于拒绝服务。</b>知道某人用户名的攻击者可以故意输错密码把对方锁住。
 * 这是按用户名锁定这一做法的固有代价，业界通行方案（按 IP 锁定、验证码前置）各有各的
 * 绕过方式与副作用，没有免费的选择。此处的应对是把窗口设得足够短（默认 15 分钟，
 * 到点自动解锁，不需要管理员介入），并允许整体关闭。
 *
 * <p><b>对不存在的账号同样计数</b>，这不是疏漏：登录链路刻意不区分"用户不存在"与"密码错误"
 * （连响应耗时都用占位哈希 dummyHash 抹平了）。若只对存在的账号锁定，那么"这个用户名会不会被锁"
 * 本身就成了一个账号枚举信道，前面所有的努力都白费。
 */
public class LoginAttemptGuard {

    private static final String KEY_PREFIX = "describeadmin:login:fail:";

    private final CacheProvider cache;
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptGuard(CacheProvider cache, int maxFailures, Duration lockDuration) {
        if (maxFailures <= 0) {
            throw new IllegalArgumentException("失败次数阈值必须为正数，当前为: " + maxFailures);
        }
        if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("锁定时长必须为正数，当前为: " + lockDuration);
        }
        this.cache = cache;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    /**
     * 在校验密码<b>之前</b>调用。已达阈值时直接抛异常，不再走后续的哈希比对。
     *
     * @param username 登录名
     */
    public void assertNotLocked(String username) {
        if (currentFailures(username) >= maxFailures) {
            throw new BizException(ResultCode.AUTH_FAILED,
                    "登录失败次数过多，账号已被临时锁定，请 " + lockDuration.toMinutes() + " 分钟后重试");
        }
    }

    /** 登录失败后调用。 */
    public void recordFailure(String username) {
        // 只自增不续期（见 CacheProvider#increment）：否则持续尝试会把锁定窗口无限延长
        cache.increment(keyOf(username), 1L, lockDuration);
    }

    /** 登录成功后调用，清零计数。 */
    public void reset(String username) {
        cache.evict(keyOf(username));
    }

    /**
     * 当前处于锁定状态的用户名集合（已去掉 {@link #KEY_PREFIX}，均为小写形式——
     * 见 {@link #keyOf(String)} 里统一大小写的处理）。
     *
     * <p>供管理侧"当前有哪些账号被锁定"的只读查询使用，不在登录主链路上调用。
     * 依赖 {@link CacheProvider#keysWithPrefix(String)}，若底层 {@code CacheProvider}
     * 不支持按前缀枚举（返回默认空集合），本方法同样返回空集合，不抛异常。
     */
    public Set<String> listLockedUsernames() {
        Set<String> locked = new LinkedHashSet<>();
        for (String key : cache.keysWithPrefix(KEY_PREFIX)) {
            long failures = cache.get(key, Long.class).orElse(0L);
            if (failures >= maxFailures) {
                locked.add(key.substring(KEY_PREFIX.length()));
            }
        }
        return locked;
    }

    /**
     * 管理员手动解锁：清除某用户名的失败计数，效果等同于提前到点自动解锁。
     *
     * @param username 登录名，大小写不敏感（见 {@link #keyOf(String)}）
     */
    public void unlock(String username) {
        cache.evict(keyOf(username));
    }

    /**
     * 当前失败次数的只读查询，不改变任何状态。
     *
     * <p>供渐进式验证码等"读计数做决策"的场景使用（见 {@code CaptchaGuard}）。
     * 与 {@link #assertNotLocked} 的区别：后者是"超过阈值就抛异常"的强制关口，
     * 本方法只读数字，判断阈值的权力交给调用方。
     */
    public long failureCount(String username) {
        return currentFailures(username);
    }

    private long currentFailures(String username) {
        return cache.get(keyOf(username), Long.class).orElse(0L);
    }

    private static String keyOf(String username) {
        // 统一大小写：数据库的 utf8mb4_general_ci 是大小写不敏感的，
        // 若键区分大小写，Admin / admin / ADMIN 会各自计数，阈值等于被放大数倍
        return KEY_PREFIX + (username == null ? "" : username.toLowerCase(Locale.ROOT));
    }
}
