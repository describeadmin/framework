package io.github.describeadmin.security.autoconfigure;

import io.github.describeadmin.cache.autoconfigure.FrameworkCacheAutoConfiguration;
import io.github.describeadmin.security.api.CaptchaProvider;
import io.github.describeadmin.security.core.CaptchaGuard;
import io.github.describeadmin.security.core.ImageCaptchaProvider;
import io.github.describeadmin.security.core.LoginAttemptGuard;
import io.github.describeadmin.security.core.LoginRiskCounter;
import io.github.describeadmin.security.core.PowCaptchaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证码相关 Bean 的装配行为。
 *
 * <p>非 Web 场景（{@link ApplicationContextRunner}）即可覆盖——{@code CaptchaProvider}/
 * {@code CaptchaGuard} 不依赖 Servlet 环境，与 {@code WebSecurityConfiguration} 无关。
 */
@DisplayName("验证码自动配置")
class FrameworkSecurityCaptchaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FrameworkCacheAutoConfiguration.class, FrameworkSecurityAutoConfiguration.class));

    @Test
    @DisplayName("默认装配：CaptchaProvider 默认实现是 PoW，CaptchaGuard、LoginRiskCounter 均存在")
    void defaultAssembly() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CaptchaProvider.class);
            assertThat(context.getBean(CaptchaProvider.class)).isInstanceOf(PowCaptchaProvider.class);
            assertThat(context).hasSingleBean(CaptchaGuard.class);
            assertThat(context).hasSingleBean(LoginRiskCounter.class);
        });
    }

    @Test
    @DisplayName("captcha.type=image：切回图形验证码")
    void typeImageSelectsImageProvider() {
        runner.withPropertyValues("describeadmin.security.captcha.type=image").run(context ->
                assertThat(context.getBean(CaptchaProvider.class)).isInstanceOf(ImageCaptchaProvider.class));
    }

    @Test
    @DisplayName("captcha.type 写错：启动失败，而不是静默回落到某个默认实现")
    void rejectsUnknownType() {
        runner.withPropertyValues("describeadmin.security.captcha.type=slider").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("describeadmin.security.captcha.type 只支持 pow / image，当前为: slider");
        });
    }

    @Test
    @DisplayName("ip 与 global 都关闭：LoginRiskCounter 不注册，CaptchaGuard 仍在（只剩用户名维度）")
    void riskCounterAbsentWhenBothDimensionsDisabled() {
        runner.withPropertyValues(
                "describeadmin.security.captcha.ip.enabled=false",
                "describeadmin.security.captcha.global.enabled=false"
        ).run(context -> {
            assertThat(context).doesNotHaveBean(LoginRiskCounter.class);
            assertThat(context).hasSingleBean(CaptchaGuard.class);
        });
    }

    @Test
    @DisplayName("只关一个维度：LoginRiskCounter 仍注册")
    void riskCounterPresentWhenOneDimensionEnabled() {
        runner.withPropertyValues("describeadmin.security.captcha.ip.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(LoginRiskCounter.class));
    }

    @Test
    @DisplayName("维度阈值非正数：启动失败，错误信息指向具体配置项")
    void rejectsInvalidDimension() {
        runner.withPropertyValues("describeadmin.security.captcha.ip.threshold=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("describeadmin.security.captcha.ip 配置无效");
        });
    }

    @Test
    @DisplayName("captcha.enabled=false：CaptchaGuard 与 LoginRiskCounter 不注册，但 CaptchaProvider（生成/校验能力本身）仍在")
    void captchaGuardCanBeDisabledIndependently() {
        runner.withPropertyValues("describeadmin.security.captcha.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CaptchaGuard.class);
            assertThat(context).doesNotHaveBean(LoginRiskCounter.class);
            assertThat(context).hasSingleBean(CaptchaProvider.class);
        });
    }

    @Test
    @DisplayName("lockout.enabled=false：CaptchaGuard 仍注册，但内部 LoginAttemptGuard 为空（fail-open）")
    void captchaGuardToleratesMissingAttemptGuard() {
        runner.withPropertyValues("describeadmin.security.lockout.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(LoginAttemptGuard.class);
            assertThat(context).hasSingleBean(CaptchaGuard.class);
        });
    }

    @Test
    @DisplayName("trigger-threshold >= lockout.max-failures：启动期直接拒绝，而不是运行到一半才发现")
    void rejectsInvalidThresholdAtStartup() {
        runner.withPropertyValues(
                "describeadmin.security.captcha.trigger-threshold=5",
                "describeadmin.security.lockout.max-failures=5"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                            "describeadmin.security.captcha.trigger-threshold(5) 必须小于 "
                                    + "describeadmin.security.lockout.max-failures(5)");
        });
    }
}
