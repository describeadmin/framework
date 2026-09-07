package io.github.describeadmin.cache.api;

/**
 * 锁的获取策略。
 *
 * <p>{@link #DEFAULT} 只出现在 {@link DistributedLock#strategy()} 的取值里，
 * 表示"跟随全局配置 {@code describeadmin.lock.default-strategy}"——
 * 默认策略是部署期的统一决策，不属于某个注解点的局部信息，
 * 因此交给配置而不是写死在注解里。显式指定 {@link #WAIT} 或
 * {@link #FAIL_FAST} 则覆盖全局配置。
 */
public enum LockStrategy {

    /** 跟随全局配置 {@code describeadmin.lock.default-strategy}。仅注解可用此值。 */
    DEFAULT,

    /** 限时等待：在 waitTimeout 内反复尝试，到期仍未获取则报错。 */
    WAIT,

    /** 快速失败：未获取锁立即报错，不等待。 */
    FAIL_FAST
}
