package io.github.describeadmin.cache.api;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 键值缓存契约。
 *
 * <p><b>为什么不直接用 Spring 的 {@code CacheManager}</b>：{@code @Cacheable} 那一套是
 * 面向"方法返回值缓存"的声明式模型，而框架需要的是显式的读写与计数
 * （登录失败次数、验证码、字典快照），这些都不是"某个方法的返回值"。
 * 用注解表达会变成绕着 API 走。业务方当然可以照常使用 Spring Cache，两者不冲突。
 *
 * <p><b>方法刻意保持得很少</b>：每多一个方法，将来每种实现都要正确实现一遍，
 * 而实现之间的语义差异（尤其是原子性）正是这类抽象最容易出问题的地方。
 * 需要新能力时用 {@code default} 方法追加，不破坏既有实现。
 *
 * <p>框架默认提供 {@code InMemoryCacheProvider}。业务方注册自己的 {@code CacheProvider}
 * Bean 即自动覆盖。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface CacheProvider {

    /**
     * 写入并设置存活时间。
     *
     * <p>同一个 key 重复写入即覆盖，存活时间一并重置。
     *
     * @param key   缓存键，不为空
     * @param value 值；{@code null} 等同于 {@link #evict}
     * @param ttl   存活时间，必须为正数
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 读取。
     *
     * <p>要求传入期望类型而不是直接返回 {@code Object}：集中式实现需要类型信息才能反序列化，
     * 若接口不带类型，Redis 实现只能靠在值里塞类名之类的手段自行还原，
     * 那会让不同实现的行为出现差异。
     *
     * <p>键不存在、已过期，或存的值与 {@code type} 不匹配时返回空 {@link Optional}——
     * <b>不要抛类型转换异常</b>，缓存未命中是正常流量。
     */
    <T> Optional<T> get(String key, Class<T> type);

    /** 删除。键不存在时静默返回。 */
    void evict(String key);

    /**
     * 原子自增，返回自增后的值。
     *
     * <p>单独给出本方法而不是让调用方自己 get-then-put，是因为那样做在并发下会丢计数——
     * 而它最典型的用途恰恰是登录失败计数，丢计数意味着爆破防护被绕过。
     *
     * <p>键不存在时按 0 起算，并以 {@code ttlWhenCreated} 设置存活时间；
     * 键已存在时<b>只自增、不续期</b>——否则持续的失败尝试会把锁定窗口无限延长，
     * 变成永久锁定。
     *
     * @param key            缓存键
     * @param delta          增量，可为负
     * @param ttlWhenCreated 键不存在时设置的存活时间，必须为正数
     * @return 自增后的值
     */
    long increment(String key, long delta, Duration ttlWhenCreated);

    /**
     * 按前缀列出当前存在的 key（不解析值内容）。
     *
     * <p><b>为什么是 default 方法</b>：本方法是在接口发布之后新增的，写成抽象方法会让
     * 所有已实现 {@code CacheProvider} 的业务方直接编译失败。默认返回空集合，语义是
     * "本实现不支持按前缀枚举"，手法同 {@code TokenStore.listActive()}。
     *
     * <p>仅用于低频的管理侧可观测性场景（例如查询当前有哪些账号处于登录锁定状态），
     * <b>不要用于高频路径</b>——集中式实现（如 Redis）按前缀枚举的开销远高于单键读写，
     * 且实现必须避免使用会阻塞整个存储的全量扫描方式。
     *
     * @param prefix 缓存键前缀，不为空
     * @return 匹配前缀且未过期的 key 集合；不支持枚举时返回空集合，<b>不要抛异常</b>
     */
    default Set<String> keysWithPrefix(String prefix) {
        return Set.of();
    }
}
