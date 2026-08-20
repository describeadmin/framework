package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LoginAttemptGuard} 的单元测试。
 *
 * <p>用真实的 {@link InMemoryCacheProvider} 而不是 mock：本类的正确性有一半落在
 * "increment 的语义是否如预期"上（是否续期、是否原子），mock 掉就等于把要验的东西假设成立了。
 */
@DisplayName("登录失败次数限制")
class LoginAttemptGuardTest {

    private final CacheProvider cache = new InMemoryCacheProvider(100);

    private LoginAttemptGuard guard(int maxFailures, Duration lockDuration) {
        return new LoginAttemptGuard(cache, maxFailures, lockDuration);
    }

    @Test
    @DisplayName("未达阈值时放行")
    void allowsBelowThreshold() {
        LoginAttemptGuard guard = guard(3, Duration.ofMinutes(15));
        guard.recordFailure("alice");
        guard.recordFailure("alice");

        assertThatNoException().isThrownBy(() -> guard.assertNotLocked("alice"));
    }

    @Test
    @DisplayName("达到阈值后拒绝，错误信息告知等待时长")
    void locksAtThreshold() {
        LoginAttemptGuard guard = guard(3, Duration.ofMinutes(15));
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("alice");
        }

        assertThatThrownBy(() -> guard.assertNotLocked("alice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("15 分钟")
                .extracting(e -> ((BizException) e).getCode())
                // 与"用户名或密码错误"用同一个码：对外不区分锁定原因属于认证失败的哪一种
                .isEqualTo(ResultCode.AUTH_FAILED.getCode());
    }

    @Test
    @DisplayName("锁定只影响当事账号，不波及其他人")
    void locksOnlyTheTargetAccount() {
        LoginAttemptGuard guard = guard(2, Duration.ofMinutes(15));
        guard.recordFailure("alice");
        guard.recordFailure("alice");

        assertThatThrownBy(() -> guard.assertNotLocked("alice")).isInstanceOf(BizException.class);
        assertThatNoException().isThrownBy(() -> guard.assertNotLocked("bob"));
    }

    @Test
    @DisplayName("登录成功后计数清零")
    void resetClearsCounter() {
        LoginAttemptGuard guard = guard(2, Duration.ofMinutes(15));
        guard.recordFailure("alice");
        guard.recordFailure("alice");
        guard.reset("alice");

        assertThatNoException().isThrownBy(() -> guard.assertNotLocked("alice"));
    }

    @Test
    @DisplayName("锁定窗口到期后自动解锁，不需要人工介入")
    void unlocksAfterWindow() throws Exception {
        LoginAttemptGuard guard = guard(1, Duration.ofMillis(80));
        guard.recordFailure("alice");
        assertThatThrownBy(() -> guard.assertNotLocked("alice")).isInstanceOf(BizException.class);

        Thread.sleep(120);

        assertThatNoException().isThrownBy(() -> guard.assertNotLocked("alice"));
    }

    @Test
    @DisplayName("持续失败不会把锁定窗口无限延长")
    void continuedFailuresDoNotExtendWindow() throws Exception {
        LoginAttemptGuard guard = guard(1, Duration.ofMillis(120));
        guard.recordFailure("alice");
        Thread.sleep(60);
        // 攻击者在窗口内继续尝试。若这次失败把存活时间重置了，锁定就变成永久的
        guard.recordFailure("alice");
        Thread.sleep(90);

        assertThatNoException().isThrownBy(() -> guard.assertNotLocked("alice"));
    }

    @Test
    @DisplayName("用户名大小写不影响计数，否则阈值等于被放大数倍")
    void isCaseInsensitive() {
        LoginAttemptGuard guard = guard(3, Duration.ofMinutes(15));
        guard.recordFailure("Alice");
        guard.recordFailure("ALICE");
        guard.recordFailure("alice");

        // 数据库排序规则 utf8mb4_general_ci 本身大小写不敏感，
        // 键若区分大小写，攻击者换个大小写就能重新获得一整轮尝试次数
        assertThatThrownBy(() -> guard.assertNotLocked("aLiCe")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("不存在的用户名同样计数——否则'会不会被锁'成为账号枚举信道")
    void countsUnknownUsernamesToo() {
        LoginAttemptGuard guard = guard(2, Duration.ofMinutes(15));
        // Guard 本身不查库，这条用例锁定的是"调用方必须对不存在的用户也调 recordFailure"
        // 这一契约（由 UsernamePasswordAuthProvider 履行），此处验证 Guard 不做任何账号存在性判断
        guard.recordFailure("no-such-user");
        guard.recordFailure("no-such-user");

        assertThatThrownBy(() -> guard.assertNotLocked("no-such-user"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("非法配置在构造时就拒绝，而不是运行到一半才出问题")
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> guard(0, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cache).isNotNull();
    }
}
