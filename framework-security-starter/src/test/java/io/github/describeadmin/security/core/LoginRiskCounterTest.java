package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("登录失败 IP / 全局计数")
class LoginRiskCounterTest {

    private static LoginRiskCounter.Dimension dim(int threshold) {
        return new LoginRiskCounter.Dimension(threshold, Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("按 IP 分别计数，全局累计所有 IP")
    void countsPerIpAndGlobally() {
        LoginRiskCounter counter = new LoginRiskCounter(new InMemoryCacheProvider(100), dim(10), dim(10));
        counter.recordFailure("10.0.0.1");
        counter.recordFailure("10.0.0.1");
        counter.recordFailure("10.0.0.2");

        assertThat(counter.ipFailures("10.0.0.1")).isEqualTo(2);
        assertThat(counter.ipFailures("10.0.0.2")).isEqualTo(1);
        assertThat(counter.globalFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("阈值判定：IP 或全局任一达到即为 true")
    void exceedsThreshold() {
        LoginRiskCounter counter = new LoginRiskCounter(new InMemoryCacheProvider(100), dim(2), dim(3));
        counter.recordFailure("10.0.0.1");
        assertThat(counter.exceedsThreshold("10.0.0.1")).isFalse();

        counter.recordFailure("10.0.0.1");
        assertThat(counter.exceedsThreshold("10.0.0.1")).isTrue();
        assertThat(counter.exceedsThreshold("10.0.0.2")).isFalse();

        counter.recordFailure("10.0.0.3");
        assertThat(counter.exceedsThreshold("10.0.0.2")).isTrue();
    }

    @Test
    @DisplayName("IP 为空：只计全局，IP 维度不判定")
    void blankIpOnlyCountsGlobal() {
        LoginRiskCounter counter = new LoginRiskCounter(new InMemoryCacheProvider(100), dim(1), dim(10));
        counter.recordFailure(null);
        counter.recordFailure("");

        assertThat(counter.globalFailures()).isEqualTo(2);
        assertThat(counter.exceedsThreshold(null)).isFalse();
    }

    @Test
    @DisplayName("关闭的维度：不计数也不判定")
    void disabledDimensionIsIgnored() {
        LoginRiskCounter ipOnly = new LoginRiskCounter(new InMemoryCacheProvider(100), dim(1), null);
        ipOnly.recordFailure("10.0.0.1");
        assertThat(ipOnly.globalFailures()).isZero();
        assertThat(ipOnly.exceedsThreshold("10.0.0.2")).isFalse();

        LoginRiskCounter globalOnly = new LoginRiskCounter(new InMemoryCacheProvider(100), null, dim(5));
        globalOnly.recordFailure("10.0.0.1");
        assertThat(globalOnly.ipFailures("10.0.0.1")).isZero();
    }

    @Test
    @DisplayName("窗口到期整体清零")
    void windowExpires() throws InterruptedException {
        LoginRiskCounter counter = new LoginRiskCounter(new InMemoryCacheProvider(100),
                new LoginRiskCounter.Dimension(1, Duration.ofMillis(50)), null);
        counter.recordFailure("10.0.0.1");
        assertThat(counter.exceedsThreshold("10.0.0.1")).isTrue();

        Thread.sleep(120);
        assertThat(counter.exceedsThreshold("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("非法参数在构造时就拒绝")
    void rejectsInvalidConstruction() {
        InMemoryCacheProvider cache = new InMemoryCacheProvider(10);
        assertThatThrownBy(() -> new LoginRiskCounter(cache, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginRiskCounter(null, dim(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dim(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginRiskCounter.Dimension(1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
