package io.github.describeadmin.cache.api;

import java.time.Duration;
import java.util.Optional;

/**
 * 锁契约：防并发的最佳努力原语。
 *
 * <p>框架默认提供单机（进程内）实现；引入 {@code framework-cache-redis-starter}
 * 后自动替换为基于 Redis 的分布式实现——上层代码（注解、{@code UniqueGuard}、
 * 直接编程调用）不用动，与 {@link CacheProvider} 是同一套可插拔模型。
 *
 * <h2>刻意不做的三件事</h2>
 * <ul>
 *   <li><b>不可重入</b>：同一线程对同一键的嵌套加锁会失败。可重入需要计数与
 *       持有者栈管理，内存版与 Redis 版的语义极易分叉，而框架内的真实场景
 *       （接口防重、定时任务防多机并发、唯一性保护）都不需要它</li>
 *   <li><b>不续期（watchdog）</b>：持有超过 TTL 的锁会被别人抢走，这是设计行为。
 *       续期意味着后台线程与"持有者宕机后锁永不释放"的新风险，v1 明确不做</li>
 *   <li><b>不是绝对互斥</b>：锁只约束<b>走同一套锁实现的并发写路径</b>。
 *       绕过应用的写入（手工 SQL、直连数据库的其他服务）不受约束。
 *       业务侧仍需幂等兜底，见 {@link UniqueGuard}</li>
 * </ul>
 *
 * <h2>故障语义：fail-close</h2>
 * 存储不可用（如 Redis 连不上）时，加锁方法抛
 * {@link io.github.describeadmin.common.api.BizException} 而不是返回空——
 * 这与 {@link CacheProvider} 的 fail-open 刻意相反：缓存读不到可以回源，
 * 锁拿不到就放行等于并发保护静默失效，宁可请求失败。
 * {@link LockHandle#close()} 例外：释放失败只记日志，锁由 TTL 兜底，
 * 不在 finally 里抛异常掩盖业务结果。
 *
 * <h2>键的约定</h2>
 * 调用方传入<b>裸键</b>（如 {@code user:username:jeff}），统一前缀
 * （{@code describeadmin.lock.key-prefix}）由实现层拼接。裸键不应自带前缀。
 * 实现方应保证超长键被截断（带哈希后缀，防大 key）。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface LockOperations {

    /**
     * 尝试加锁，不等待。
     *
     * @param key 裸键，不为空
     * @param ttl 锁的存活时间，必须为正数——持有者宕机后锁靠它自动开放，
     *            取值应显著大于业务的正常执行时长
     * @return 成功返回句柄；键已被占用返回空
     * @throws io.github.describeadmin.common.api.BizException 存储不可用（fail-close）
     */
    Optional<LockHandle> tryLock(String key, Duration ttl);

    /**
     * 限时等待加锁：在 {@code waitTimeout} 内反复尝试，到期仍未获取则返回空。
     *
     * <p>实现为轮询重试（内存与 Redis 同一套语义、同一套测试可断言），
     * 而不是 pub/sub 通知——v1 的等待场景（接口防重复提交）对轮询的
     * 百毫秒级延迟不敏感。
     *
     * <p><b>不要把本方法用在定时任务上</b>：节点 A 执行任务期间节点 B 一直等，
     * A 释放后 B 再执行一遍——恰好制造了要防的重复执行。定时任务请用
     * {@link #tryLock(String, Duration)}（拿不到即跳过）。
     *
     * @param key         裸键，不为空
     * @param ttl         锁的存活时间，必须为正数
     * @param waitTimeout 最长等待时间，必须为正数
     * @return 成功返回句柄；超时未获取返回空
     * @throws io.github.describeadmin.common.api.BizException 存储不可用（fail-close）
     */
    Optional<LockHandle> lock(String key, Duration ttl, Duration waitTimeout);
}
