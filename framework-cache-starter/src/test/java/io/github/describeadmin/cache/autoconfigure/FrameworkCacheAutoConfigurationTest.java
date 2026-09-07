package io.github.describeadmin.cache.autoconfigure;

import io.github.describeadmin.cache.api.LockOperations;
import io.github.describeadmin.cache.core.InMemoryLockOperations;
import io.github.describeadmin.cache.core.LockAspect;
import io.github.describeadmin.cache.api.UniqueGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * framework-cache-starter 锁能力的装配测试：内存兜底注册、配置校验、
 * 业务方覆盖。与 redis 插件仓的装配测试互为镜像——那边验证"插件接管"，
 * 这边验证"没有插件时的默认形态"。
 */
@DisplayName("锁装配")
class FrameworkCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkCacheAutoConfiguration.class));

    @Test
    @DisplayName("默认装配：内存锁 + UniqueGuard + 切面 + 启动校验器")
    void defaultBeansRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LockOperations.class);
            assertThat(context.getBean(LockOperations.class)).isInstanceOf(InMemoryLockOperations.class);
            assertThat(context).hasSingleBean(UniqueGuard.class);
            assertThat(context).hasSingleBean(LockAspect.class);
            assertThat(context).hasSingleBean(io.github.describeadmin.cache.core.LockStartupValidator.class);
        });
    }

    @Test
    @DisplayName("default-strategy 配成 DEFAULT：启动即拒绝（\"默认跟随默认\"没有意义）")
    void defaultStrategyDefaultRejected() {
        runner.withPropertyValues("describeadmin.lock.default-strategy=DEFAULT").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("default-strategy");
        });
    }

    @Test
    @DisplayName("default-strategy=FAIL_FAST：接受，注解未显式指定时按它行事")
    void failFastDefaultAccepted() {
        runner.withPropertyValues("describeadmin.lock.default-strategy=FAIL_FAST").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FrameworkLockProperties.class).getDefaultStrategy().name())
                    .isEqualTo("FAIL_FAST");
        });
    }

    @Test
    @DisplayName("key-prefix 为空：启动即拒绝")
    void blankKeyPrefixRejected() {
        runner.withPropertyValues("describeadmin.lock.key-prefix=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("key-prefix");
        });
    }

    @Test
    @DisplayName("自定义 key-prefix：接受并生效")
    void customKeyPrefixAccepted() {
        runner.withPropertyValues("describeadmin.lock.key-prefix=app2:lock:").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FrameworkLockProperties.class).getKeyPrefix())
                    .isEqualTo("app2:lock:");
        });
    }

    @Test
    @DisplayName("业务方自定义 LockOperations 优先于框架兜底")
    void businessLockOperationsWins() {
        InMemoryLockOperations custom = new InMemoryLockOperations("business:");
        runner.withBean("customLock", LockOperations.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(LockOperations.class);
            assertThat(context.getBean(LockOperations.class))
                    .as("业务方显式注册的 Bean 必须赢过框架兜底，否则无法覆盖框架行为")
                    .isSameAs(custom);
        });
    }

    /** 配置校验的异常会被容器包装成 BeanCreationException，断言必须看根因。 */
    private static Throwable rootCause(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        Throwable root = context.getStartupFailure();
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
