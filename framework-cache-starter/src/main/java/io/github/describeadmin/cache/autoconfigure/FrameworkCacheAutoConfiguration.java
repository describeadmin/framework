package io.github.describeadmin.cache.autoconfigure;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework-cache-starter 的自动配置。
 *
 * <p>只注册一个零依赖的内存实现作为兜底。引入 {@code framework-cache-redis-starter}
 * 之类的插件时，插件注册自己的 {@link CacheProvider}，本处的
 * {@code @ConditionalOnMissingBean} 自动让位。
 *
 * <p>插件侧必须声明 {@code @AutoConfiguration(before = FrameworkCacheAutoConfiguration.class)}
 * 或让自己的 Bean 定义先被评估——{@code @ConditionalOnMissingBean} 只看当前已注册的
 * Bean 定义，顺序不对会出现"插件引了却没生效"，且没有任何报错
 * （VERSION_BASELINE.md 发现 ⑦ 记录过同类问题）。
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkCacheProperties.class)
public class FrameworkCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheProvider.class)
    public CacheProvider inMemoryCacheProvider(FrameworkCacheProperties properties) {
        return new InMemoryCacheProvider(properties.getMaxSize());
    }
}
