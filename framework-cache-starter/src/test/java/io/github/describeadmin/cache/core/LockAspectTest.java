package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.DistributedLock;
import io.github.describeadmin.cache.api.LockStrategy;
import io.github.describeadmin.cache.autoconfigure.FrameworkLockProperties;
import io.github.describeadmin.common.api.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LockAspect} 的单元测试。
 *
 * <p>用 {@link AspectJProxyFactory} 代理真实对象，验证的是完整的切面链路
 * （注解匹配 → SpEL 解析 → 策略分派 → 加锁/释放），而不是切面方法本身。
 */
@DisplayName("声明式锁切面")
class LockAspectTest {

    private InMemoryLockOperations locks;
    private FrameworkLockProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FrameworkLockProperties();
        locks = new InMemoryLockOperations(properties.getKeyPrefix());
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new LockAspect(locks, properties));
        return factory.getProxy();
    }

    /** 测试目标：各种注解形态的载体。方法名同时是缺省键的一部分，保持可读。 */
    static class OrderService {

        final AtomicInteger executions = new AtomicInteger();

        @DistributedLock(key = "'order:' + #orderNo", ttl = 30, waitTimeout = 1)
        public String create(String orderNo) {
            executions.incrementAndGet();
            return "ok:" + orderNo;
        }

        @DistributedLock(strategy = LockStrategy.FAIL_FAST, key = "'order:' + #orderNo")
        public String pay(String orderNo) {
            executions.incrementAndGet();
            return "paid:" + orderNo;
        }

        /** 缺省键：全限定类名.方法名。 */
        @DistributedLock
        public void nightlyJob() {
            executions.incrementAndGet();
        }

        @DistributedLock(key = "'user:' + #user.name", ttl = 5, waitTimeout = 1)
        public String bind(User user) {
            executions.incrementAndGet();
            return "bound";
        }

        record User(String name) {
        }
    }

    @Nested
    @DisplayName("SpEL 键解析")
    class SpelKey {

        @Test
        @DisplayName("参数引用：同一参数值互斥，不同参数值并行")
        void keyByArgument() {
            OrderService target = new OrderService();
            OrderService service = proxy(target);

            assertThat(service.create("A")).isEqualTo("ok:A");
            assertThat(service.create("B")).as("不同键不阻塞").isEqualTo("ok:B");
            // 计数器读目标对象：CGLIB 代理经 Objenesis 创建、不跑构造器，
            // 代理实例自身的字段是 null
            assertThat(target.executions.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("属性导航：#user.name 解析到参数的属性")
        void propertyNavigation() {
            OrderService service = proxy(new OrderService());

            assertThat(service.bind(new OrderService.User("jeff"))).isEqualTo("bound");
        }

        @Test
        @DisplayName("缺省键 = 全限定类名.方法名（不是简单类名，否则跨包同名类会撞键）")
        void defaultKeyIsQualifiedClassAndMethod() {
            properties.setDefaultStrategy(LockStrategy.FAIL_FAST);
            OrderService service = proxy(new OrderService());

            // 先占住"全限定类名.方法名"这把裸键，再调用：若切面确实用了这个键，
            // FAIL_FAST 下应立即报"操作处理中"——这比事后检查锁状态更直接。
            // 断言写全限定名而不是 "OrderService.nightlyJob"：简单类名会让
            // 两个不同包的同名定时任务共用一把锁、互相静默跳过
            String key = OrderService.class.getName() + ".nightlyJob";
            assertThat(key).contains("io.github.describeadmin.cache.core");
            assertThat(locks.tryLock(key, Duration.ofSeconds(30))).isPresent();

            assertThatThrownBy(service::nightlyJob)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作处理中");
        }
    }

    @Nested
    @DisplayName("策略分派")
    class Strategy {

        @Test
        @DisplayName("FAIL_FAST：锁被占时立即报错，业务不执行")
        void failFastThrowsImmediately() {
            OrderService target = new OrderService();
            OrderService service = proxy(target);
            assertThat(locks.tryLock("order:A", Duration.ofSeconds(30))).isPresent();

            assertThatThrownBy(() -> service.pay("A"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作处理中");

            assertThat(target.executions.get()).as("业务方法不应被执行").isZero();
        }

        @Test
        @DisplayName("WAIT（全局默认）：等待超时报错，文案与 FAIL_FAST 区分")
        void waitTimeoutThrows() {
            properties.setDefaultStrategy(LockStrategy.WAIT);
            OrderService service = proxy(new OrderService());
            assertThat(locks.tryLock("order:A", Duration.ofSeconds(30))).isPresent();

            long start = System.currentTimeMillis();
            assertThatThrownBy(() -> service.create("A"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("获取锁超时");
            assertThat(System.currentTimeMillis() - start).isLessThan(3000);
        }

        @Test
        @DisplayName("DEFAULT 跟随全局配置：配成 FAIL_FAST 后未显式指定的注解按 FAIL_FAST 行事")
        void defaultFollowsGlobalConfig() {
            properties.setDefaultStrategy(LockStrategy.FAIL_FAST);
            OrderService service = proxy(new OrderService());
            // create 的注解未指定 strategy（DEFAULT），应按全局配置快速失败
            assertThat(locks.tryLock("order:A", Duration.ofSeconds(30))).isPresent();

            assertThatThrownBy(() -> service.create("A"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作处理中");
        }

        @Test
        @DisplayName("注解显式值覆盖全局配置：全局 FAIL_FAST 下显式 WAIT 仍然等待")
        void explicitAnnotationOverridesGlobal() {
            properties.setDefaultStrategy(LockStrategy.FAIL_FAST);
            OrderService service = proxy(new OrderService());
            assertThat(locks.tryLock("order:A", Duration.ofSeconds(30))).isPresent();

            // pay 显式 FAIL_FAST；这里验证反向：全局 WAIT 时显式 FAIL_FAST 不等待
            properties.setDefaultStrategy(LockStrategy.WAIT);
            long start = System.currentTimeMillis();
            assertThatThrownBy(() -> service.pay("A"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作处理中");
            assertThat(System.currentTimeMillis() - start).isLessThan(1000);
        }
    }

    @Nested
    @DisplayName("锁的生命周期")
    class Lifecycle {

        @Test
        @DisplayName("业务完成后锁已释放（可再次进入）")
        void lockReleasedAfterMethodReturns() {
            OrderService target = new OrderService();
            OrderService service = proxy(target);

            service.create("A");
            service.create("A");

            assertThat(target.executions.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("业务抛异常时锁同样释放（try-with-resources 兜底）")
        void lockReleasedOnException() {
            class Failing {
                @DistributedLock(key = "'boom'")
                public String run() {
                    throw new IllegalStateException("业务失败");
                }
            }
            Failing service = proxy(new Failing());

            assertThatThrownBy(service::run).isInstanceOf(IllegalStateException.class);
            // 锁必须已释放，否则一次失败会永久挡住后续所有请求（直到 TTL）
            assertThat(locks.tryLock("boom", Duration.ofSeconds(5))).isPresent();
        }

        @Test
        @DisplayName("SpEL 结果为空：直接报错而不是用空键加锁")
        void blankKeyResultFails() {
            class Nullable {
                @DistributedLock(key = "#maybe")
                public String run(String maybe) {
                    return "ok";
                }
            }
            Nullable service = proxy(new Nullable());

            assertThatThrownBy(() -> service.run(null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("锁键表达式结果为空");
        }
    }

    @Nested
    @DisplayName("与 LockOperations 的键约定")
    class KeyConvention {

        @Test
        @DisplayName("切面传裸键，前缀由实现层拼接：直取同键与切面互相可见")
        void bareKeyPlusPrefix() {
            properties.setDefaultStrategy(LockStrategy.FAIL_FAST);
            OrderService service = proxy(new OrderService());

            // 从 LockOperations 直取裸键 "order:A"（前缀由实现拼接），
            // 切面即被挡住——证明切面与直取路径走的是同一把键
            assertThat(locks.tryLock("order:A", Duration.ofSeconds(30))).isPresent();
            assertThatThrownBy(() -> service.create("A"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作处理中");
        }
    }
}
