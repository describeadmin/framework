package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.DistributedLock;
import io.github.describeadmin.cache.api.LockStrategy;
import io.github.describeadmin.cache.autoconfigure.FrameworkLockProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LockStartupValidator} 的测试：@Scheduled + 等待策略 = 拒绝启动。
 *
 * <p>配置错误必须在部署当天暴露，而不是三个月后对账时——用例覆盖
 * 注解显式 WAIT、DEFAULT 跟随全局 WAIT 两种"最终生效为 WAIT"的路径。
 */
@DisplayName("定时任务锁策略的启动期校验")
class LockStartupValidatorTest {

    /** 每个用例独立 runner：全局策略是"本用例的世界观"，不能跨用例累积 Bean。 */
    private ApplicationContextRunner runner(LockStrategy globalStrategy) {
        return new ApplicationContextRunner()
                .withBean(FrameworkLockProperties.class, () -> {
                    FrameworkLockProperties properties = new FrameworkLockProperties();
                    properties.setDefaultStrategy(globalStrategy);
                    return properties;
                })
                .withBean(LockStartupValidator.class);
    }

    /** 被扫描的目标 Bean：方法上的注解组合由各用例的子类决定。 */
    static class BaseJob {
    }

    static class WaitScheduledJob extends BaseJob {
        @DistributedLock(strategy = LockStrategy.WAIT)
        @Scheduled(fixedDelay = 60_000)
        public void run() {
        }
    }

    static class DefaultStrategyJob extends BaseJob {
        @DistributedLock
        @Scheduled(fixedDelay = 60_000)
        public void run() {
        }
    }

    static class FailFastScheduledJob extends BaseJob {
        @DistributedLock(strategy = LockStrategy.FAIL_FAST)
        @Scheduled(fixedDelay = 60_000)
        public void run() {
        }
    }

    static class WaitNonScheduledJob extends BaseJob {
        // 没配 @Scheduled 的 WAIT 是合法的：接口防重复提交的常见策略
        @DistributedLock(strategy = LockStrategy.WAIT)
        public void handle() {
        }
    }

    @Test
    @DisplayName("注解显式 WAIT + @Scheduled：拒绝启动")
    void explicitWaitOnScheduledFails() {
        runner(LockStrategy.WAIT).withBean(WaitScheduledJob.class).run(context -> {
            assertThat(context).hasFailed();
            // 配置校验的异常会被容器包装成 BeanCreationException，断言必须看根因
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAIL_FAST");
        });
    }

    @Test
    @DisplayName("注解 DEFAULT + 全局配置 WAIT：同样拒绝——生效策略才是判断依据")
    void defaultStrategyFollowingGlobalWaitFails() {
        runner(LockStrategy.WAIT).withBean(DefaultStrategyJob.class).run(context -> {
            assertThat(context).hasFailed();
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DefaultStrategyJob.run");
        });
    }

    @Test
    @DisplayName("注解 DEFAULT + 全局配置 FAIL_FAST：放行")
    void defaultStrategyFollowingGlobalFailFastPasses() {
        runner(LockStrategy.FAIL_FAST).withBean(DefaultStrategyJob.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("显式 FAIL_FAST + @Scheduled：放行（定时任务的正确姿势）")
    void explicitFailFastPasses() {
        runner(LockStrategy.WAIT).withBean(FailFastScheduledJob.class).run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("WAIT 用在非定时任务方法上：放行，接口防重的正当场景")
    void waitOnNonScheduledMethodPasses() {
        runner(LockStrategy.WAIT).withBean(WaitNonScheduledJob.class).run(context ->
                assertThat(context).hasNotFailed());
    }
}
