package io.github.describeadmin.cache.autoconfigure;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.api.LockOperations;
import io.github.describeadmin.cache.api.LockStrategy;
import io.github.describeadmin.cache.api.UniqueGuard;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.cache.core.InMemoryLockOperations;
import io.github.describeadmin.cache.core.LockAspect;
import io.github.describeadmin.cache.core.LockStartupValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework-cache-starter 的自动配置。
 *
 * <p>注册两类兜底实现：零依赖的内存 {@link CacheProvider} 与零依赖的进程内
 * {@link LockOperations}。引入 {@code framework-cache-redis-starter} 之类的插件时，
 * 插件注册自己的实现，本处的 {@code @ConditionalOnMissingBean} 自动让位。
 *
 * <p>插件侧必须声明 {@code @AutoConfiguration(before = FrameworkCacheAutoConfiguration.class)}
 * 或让自己的 Bean 定义先被评估——{@code @ConditionalOnMissingBean} 只看当前已注册的
 * Bean 定义，顺序不对会出现"插件引了却没生效"，且没有任何报错
 * （VERSION_BASELINE.md 发现 ⑦ 记录过同类问题）。
 *
 * <p>锁配置（{@code describeadmin.lock.*}）的非法取值在构造函数里拒绝启动——
 * 配置错误应该在部署当天暴露，而不是某个请求上表现异常。
 */
@AutoConfiguration
@EnableConfigurationProperties({FrameworkCacheProperties.class, FrameworkLockProperties.class})
public class FrameworkCacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FrameworkCacheAutoConfiguration.class);

    private final FrameworkLockProperties lockProperties;

    public FrameworkCacheAutoConfiguration(FrameworkLockProperties lockProperties) {
        // 放在构造函数里：真的装配本配置类时才校验
        if (lockProperties.getDefaultStrategy() == null
                || lockProperties.getDefaultStrategy() == LockStrategy.DEFAULT) {
            throw new IllegalStateException("describeadmin.lock.default-strategy 只接受 WAIT 或 FAIL_FAST，"
                    + "配成 DEFAULT 没有意义（\"默认跟随默认\"）");
        }
        if (lockProperties.getKeyPrefix() == null || lockProperties.getKeyPrefix().isBlank()) {
            throw new IllegalStateException("describeadmin.lock.key-prefix 不能为空");
        }
        this.lockProperties = lockProperties;
    }

    @Bean
    @ConditionalOnMissingBean(CacheProvider.class)
    public CacheProvider inMemoryCacheProvider(FrameworkCacheProperties properties) {
        return new InMemoryCacheProvider(properties.getMaxSize());
    }

    /**
     * 进程内锁兜底。<b>多实例部署下互不感知</b>——需要跨实例互斥时引入
     * framework-cache-redis-starter。日志放在 Bean 方法里而不是构造函数里：
     * 只有真的生效了才打印，插件接管时本方法不执行，不会误导。
     */
    @Bean
    @ConditionalOnMissingBean(LockOperations.class)
    public LockOperations inMemoryLockOperations() {
        log.info("锁实现: InMemoryLockOperations（进程内，单实例部署适用），默认策略: {}，键前缀: {}",
                lockProperties.getDefaultStrategy(), lockProperties.getKeyPrefix());
        return new InMemoryLockOperations(lockProperties.getKeyPrefix());
    }

    /** 键级唯一性保护组件，供"check 唯一 → 写入"类场景串行化使用。 */
    @Bean
    @ConditionalOnMissingBean(UniqueGuard.class)
    public UniqueGuard uniqueGuard(LockOperations lockOperations) {
        return new UniqueGuard(lockOperations);
    }

    /** {@code @DistributedLock} 切面。order=1000 的理由见 {@link LockAspect} 的类注释。 */
    @Bean
    @ConditionalOnMissingBean(LockAspect.class)
    public LockAspect lockAspect(LockOperations lockOperations) {
        return new LockAspect(lockOperations, lockProperties);
    }

    /**
     * 启动期校验（@Scheduled + 等待策略 = 拒绝启动）。
     * {@link SmartInitializingSingleton} 在全部单例就绪后只读类型、不触发实例化。
     */
    @Bean
    @ConditionalOnMissingBean(LockStartupValidator.class)
    public LockStartupValidator lockStartupValidator(FrameworkLockProperties properties,
                                                     ConfigurableListableBeanFactory beanFactory) {
        return new LockStartupValidator(properties, beanFactory);
    }
}
