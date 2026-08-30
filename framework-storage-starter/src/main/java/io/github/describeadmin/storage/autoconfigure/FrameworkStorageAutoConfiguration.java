package io.github.describeadmin.storage.autoconfigure;

import io.github.describeadmin.storage.api.StorageProvider;
import io.github.describeadmin.storage.core.LocalFileStorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework-storage-starter 的自动配置。
 *
 * <p>只注册一个零依赖的本地磁盘实现作为兜底。引入以对象存储为后端的插件时，
 * 插件注册自己的 {@link StorageProvider}，本处的 {@code @ConditionalOnMissingBean}
 * 自动让位。
 *
 * <p>插件侧必须声明 {@code @AutoConfiguration(before = FrameworkStorageAutoConfiguration.class)}
 * 或让自己的 Bean 定义先被评估——{@code @ConditionalOnMissingBean} 只看当前已注册的
 * Bean 定义，顺序不对会出现"插件引了却没生效"，且没有任何报错
 * （与 {@code FrameworkCacheAutoConfiguration} 面对的是同一个陷阱）。
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkStorageProperties.class)
public class FrameworkStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageProvider.class)
    public StorageProvider localFileStorageProvider(FrameworkStorageProperties properties) {
        FrameworkStorageProperties.Local local = properties.getLocal();
        return new LocalFileStorageProvider(local.getRootDir(), local.getUrlPrefix(), local.isOverwriteExisting());
    }
}
