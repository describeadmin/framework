package io.github.describeadmin.cache.autoconfigure;

import io.github.describeadmin.cache.api.LockStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 锁的全局配置，前缀 {@code describeadmin.lock}。
 *
 * <p>内存实现与 Redis 插件共用这一个配置对象——插件不另立自己的锁配置项，
 * 保证换实现时键名与默认策略完全不变。多个应用共用一个 Redis 实例时，
 * 用 {@code key-prefix} 区分彼此。
 *
 * <p>非法取值（default-strategy 配成 DEFAULT、key-prefix 为空）在
 * {@code FrameworkCacheAutoConfiguration} 构造时拒绝启动，而不是运行到
 * 某个请求上才表现异常。
 */
@ConfigurationProperties(prefix = "describeadmin.lock")
public class FrameworkLockProperties {

    /**
     * {@code @DistributedLock} 未显式指定 strategy 时的默认策略。
     *
     * <p>只接受 {@code WAIT} / {@code FAIL_FAST}。配成 {@code DEFAULT} 没有意义
     * （"默认跟随默认"），装配期直接拒绝。
     */
    private LockStrategy defaultStrategy = LockStrategy.WAIT;

    /**
     * 锁键统一前缀，由 {@code LockOperations} 的实现层拼接。
     *
     * <p>内存实现与 Redis 实现产出完全相同的键名，替换实现不改变锁语义。
     * 注意 Redis 插件自己的 {@code describeadmin.cache.redis.key-prefix}
     * <b>不</b>作用于锁键——多应用共用 Redis 时请改本项。
     */
    private String keyPrefix = "describeadmin:lock:";

    public LockStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(LockStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
