package io.github.describeadmin.cache.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-cache-starter 的配置项，前缀 {@code describeadmin.cache}。
 */
@ConfigurationProperties(prefix = "describeadmin.cache")
public class FrameworkCacheProperties {

    /**
     * 内存缓存的条目数上限。
     *
     * <p>只对框架内置的内存实现有效；换成集中式实现（如 Redis）后本项被忽略，
     * 容量由中间件自身的策略决定。
     *
     * <p>上限存在的理由是缓存键常常含用户输入（用户名、IP），无上限意味着
     * 任何人都能通过构造大量不同的键把堆撑爆。达到上限后淘汰最接近过期的条目。
     */
    private int maxSize = 10_000;

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
}
