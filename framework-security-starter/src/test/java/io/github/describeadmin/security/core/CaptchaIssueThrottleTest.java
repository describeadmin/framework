package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.common.api.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CaptchaIssueThrottle")
class CaptchaIssueThrottleTest {

    @Test
    @DisplayName("阈值以内放行，超过即拒绝；按 IP 分开计数")
    void limitsPerIp() {
        CaptchaIssueThrottle throttle = new CaptchaIssueThrottle(new InMemoryCacheProvider(100), 3, Duration.ofMinutes(1));

        for (int i = 0; i < 3; i++) {
            throttle.acquire("10.0.0.1");
        }
        assertThatThrownBy(() -> throttle.acquire("10.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessage("获取验证码过于频繁，请稍后再试");
        assertThatCode(() -> throttle.acquire("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("窗口过期后重新计数")
    void windowExpires() throws InterruptedException {
        CaptchaIssueThrottle throttle = new CaptchaIssueThrottle(new InMemoryCacheProvider(100), 1, Duration.ofMillis(50));

        throttle.acquire("10.0.0.1");
        assertThatThrownBy(() -> throttle.acquire("10.0.0.1")).isInstanceOf(BizException.class);
        Thread.sleep(80);
        assertThatCode(() -> throttle.acquire("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("拿不到 IP 时不计数")
    void blankIpIsNotCounted() {
        CaptchaIssueThrottle throttle = new CaptchaIssueThrottle(new InMemoryCacheProvider(100), 1, Duration.ofMinutes(1));

        for (int i = 0; i < 5; i++) {
            throttle.acquire("");
            throttle.acquire(null);
        }
    }

    @Test
    @DisplayName("非法参数构造即失败")
    void rejectsInvalidConstruction() {
        InMemoryCacheProvider cache = new InMemoryCacheProvider(100);
        assertThatThrownBy(() -> new CaptchaIssueThrottle(null, 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaptchaIssueThrottle(cache, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaptchaIssueThrottle(cache, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
