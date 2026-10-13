package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.CaptchaChallenge;
import io.github.describeadmin.security.api.CaptchaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CaptchaGuard} 的单元测试。
 *
 * <p>{@link CaptchaProvider} 用一个可控的假实现——本类测的是"该不该拦、拦成什么错误码"
 * 这套编排逻辑，不是验证码本身怎么生成，用假实现能精确控制"验证结果对/错"这两条分支。
 */
@DisplayName("渐进式验证码编排")
class CaptchaGuardTest {

    /** 永远校验通过的假实现，captchaId 固定为 "ok"。 */
    private static final CaptchaProvider ALWAYS_PASS = new CaptchaProvider() {
        @Override
        public CaptchaChallenge generate() {
            return new CaptchaChallenge("ok", "fake", Map.of());
        }

        @Override
        public boolean verify(String captchaId, String answer) {
            return "ok".equals(captchaId);
        }
    };

    /** 永远校验失败的假实现。 */
    private static final CaptchaProvider ALWAYS_FAIL = new CaptchaProvider() {
        @Override
        public CaptchaChallenge generate() {
            return new CaptchaChallenge("bad", "fake", Map.of());
        }

        @Override
        public boolean verify(String captchaId, String answer) {
            return false;
        }
    };

    private LoginAttemptGuard attemptGuard() {
        CacheProvider cache = new InMemoryCacheProvider(100);
        return new LoginAttemptGuard(cache, 10, Duration.ofMinutes(15));
    }

    private Map<String, Object> loginBody(String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        return body;
    }

    @Test
    @DisplayName("失败次数低于阈值：即使缺验证码字段也放行")
    void allowsBelowThresholdEvenWithoutCaptchaFields() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        attemptGuard.recordFailure("alice");
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard, 3, List.of("password"));

        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("password", loginBody("alice")));
    }

    @Test
    @DisplayName("达到阈值、缺验证码字段：要求验证码")
    void requiresCaptchaAtThreshold() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        for (int i = 0; i < 3; i++) {
            attemptGuard.recordFailure("alice");
        }
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard, 3, List.of("password"));

        assertThatThrownBy(() -> guard.verifyIfRequired("password", loginBody("alice")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_REQUIRED.getCode());
    }

    @Test
    @DisplayName("达到阈值、验证码错误：拒绝")
    void rejectsWrongCaptchaAtThreshold() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        for (int i = 0; i < 3; i++) {
            attemptGuard.recordFailure("alice");
        }
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_FAIL, attemptGuard, 3, List.of("password"));

        Map<String, Object> body = loginBody("alice");
        body.put("captchaId", "bad");
        body.put("captchaCode", "XXXX");

        assertThatThrownBy(() -> guard.verifyIfRequired("password", body))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_INVALID.getCode());
    }

    @Test
    @DisplayName("达到阈值、验证码正确：放行")
    void allowsCorrectCaptchaAtThreshold() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        for (int i = 0; i < 3; i++) {
            attemptGuard.recordFailure("alice");
        }
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard, 3, List.of("password"));

        Map<String, Object> body = loginBody("alice");
        body.put("captchaId", "ok");
        body.put("captchaCode", "ANYTHING");

        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("password", body));
    }

    @Test
    @DisplayName("登录方式不在 applicableTypes 内：无论失败多少次都不拦截")
    void doesNotApplyToUnlistedTypes() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        for (int i = 0; i < 10; i++) {
            attemptGuard.recordFailure("alice");
        }
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_FAIL, attemptGuard, 3, List.of("password"));

        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("email", loginBody("alice")));
    }

    @Test
    @DisplayName("attemptGuard 为 null（等价于失败次数限制被关闭）：始终不拦截，fail-open")
    void failsOpenWhenAttemptGuardIsNull() {
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_FAIL, null, 3, List.of("password"));

        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("password", loginBody("alice")));
    }

    @Test
    @DisplayName("用户名为空：交给 AuthProvider 自己拒绝，不在这里重复处理")
    void skipsWhenUsernameBlank() {
        LoginAttemptGuard attemptGuard = attemptGuard();
        for (int i = 0; i < 10; i++) {
            attemptGuard.recordFailure("");
        }
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_FAIL, attemptGuard, 3, List.of("password"));

        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("password", new HashMap<>()));
    }

    private LoginRiskCounter riskCounter(int ipThreshold, int globalThreshold) {
        return new LoginRiskCounter(new InMemoryCacheProvider(100),
                new LoginRiskCounter.Dimension(ipThreshold, Duration.ofMinutes(10)),
                new LoginRiskCounter.Dimension(globalThreshold, Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("密码喷洒：多个用户名各错一次，同一 IP 达到阈值后对任意用户名都要求验证码")
    void ipDimensionCatchesPasswordSpraying() {
        LoginRiskCounter counter = riskCounter(3, 1000);
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard(), counter, 3, List.of("password"));
        for (String user : List.of("u1", "u2", "u3")) {
            guard.verifyIfRequired("password", loginBody(user), "10.0.0.1");
            guard.recordFailure("password", "10.0.0.1");
        }

        assertThatThrownBy(() -> guard.verifyIfRequired("password", loginBody("u4"), "10.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_REQUIRED.getCode());
        // 别的 IP 不受影响
        assertThatNoException().isThrownBy(
                () -> guard.verifyIfRequired("password", loginBody("u4"), "10.0.0.2"));
    }

    @Test
    @DisplayName("僵尸网络：每个 IP 只错一次，全局达到阈值后所有来源都要求验证码")
    void globalDimensionCatchesDistributedSpraying() {
        LoginRiskCounter counter = riskCounter(1000, 3);
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard(), counter, 3, List.of("password"));
        for (int i = 1; i <= 3; i++) {
            guard.recordFailure("password", "10.0.0." + i);
        }

        assertThatThrownBy(() -> guard.verifyIfRequired("password", loginBody("bob"), "10.9.9.9"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_REQUIRED.getCode());
    }

    @Test
    @DisplayName("IP 维度触发后带正确验证码：放行")
    void allowsCorrectCaptchaWhenIpDimensionTriggered() {
        LoginRiskCounter counter = riskCounter(1, 1000);
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, attemptGuard(), counter, 3, List.of("password"));
        guard.recordFailure("password", "10.0.0.1");

        Map<String, Object> body = loginBody("alice");
        body.put("captchaId", "ok");
        body.put("captchaCode", "42");
        assertThatNoException().isThrownBy(() -> guard.verifyIfRequired("password", body, "10.0.0.1"));
    }

    @Test
    @DisplayName("锁定关闭（attemptGuard 为 null）：用户名维度不触发，但 IP 维度照常工作")
    void ipDimensionWorksWithoutAttemptGuard() {
        LoginRiskCounter counter = riskCounter(1, 1000);
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_PASS, null, counter, 3, List.of("password"));
        guard.recordFailure("password", "10.0.0.1");

        assertThatThrownBy(() -> guard.verifyIfRequired("password", loginBody("alice"), "10.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.CAPTCHA_REQUIRED.getCode());
    }

    @Test
    @DisplayName("不在 applicableTypes 内的登录方式：既不计数，也不拦截")
    void riskDimensionsIgnoreUnlistedTypes() {
        LoginRiskCounter counter = riskCounter(1, 1);
        CaptchaGuard guard = new CaptchaGuard(ALWAYS_FAIL, attemptGuard(), counter, 3, List.of("password"));
        guard.recordFailure("email", "10.0.0.1");

        assertThat(counter.ipFailures("10.0.0.1")).isZero();
        assertThat(counter.globalFailures()).isZero();
        assertThatNoException().isThrownBy(
                () -> guard.verifyIfRequired("password", loginBody("alice"), "10.0.0.1"));
    }

    @Test
    @DisplayName("非法构造参数在构造时就拒绝")
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new CaptchaGuard(null, attemptGuard(), 3, List.of("password")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaptchaGuard(ALWAYS_PASS, attemptGuard(), 0, List.of("password")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attemptGuard()).isNotNull();
    }
}
