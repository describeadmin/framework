package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.CacheProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link CacheProvider} 的内存实现，框架默认值。
 *
 * <p><b>适用范围与已知局限（部署前必读）</b>：
 * <ul>
 *   <li>数据存活在进程内存中，<b>应用重启后全部丢失</b></li>
 *   <li><b>不支持多实例部署</b>——实例 A 写入的键在实例 B 上读不到。
 *       对登录失败锁定意味着：N 个实例下，攻击者实际可尝试的次数是配置值的 N 倍</li>
 *   <li>容量有上限（见 {@code maxSize}），满了会淘汰最接近过期的条目</li>
 * </ul>
 * 与 {@code InMemoryTokenStore} 是同一组取舍：单机部署下可接受，
 * 需要多实例或重启不丢时换成集中式实现，上层代码不用动。
 *
 * <p><b>容量上限带来的一个诚实的弱点</b>：缓存键含用户输入（如用户名）时，
 * 攻击者可以用大量随机键把表塞满，把真实用户的锁定记录挤掉。
 * 默认上限取得足够大以致这种攻击本身先触发别的告警，但这条限制客观存在，
 * 对爆破防护有硬要求的部署应使用集中式实现。
 *
 * <p>过期数据采用<b>惰性清理 + 写入时按量清理</b>，不起后台线程——
 * 与 {@code InMemoryTokenStore} 保持一致的策略，理由也相同。
 */
public class InMemoryCacheProvider implements CacheProvider {

    /** 触发一次全量清理的写入次数间隔。取值不敏感，只是避免每次写入都全表扫。 */
    private static final int SWEEP_INTERVAL = 256;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong writeCount = new AtomicLong();
    private final int maxSize;

    public InMemoryCacheProvider(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("缓存容量上限必须为正数，当前为: " + maxSize);
        }
        this.maxSize = maxSize;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        requireKey(key);
        if (value == null) {
            evict(key);
            return;
        }
        requireTtl(ttl);
        ensureCapacity();
        store.put(key, new Entry(value, Instant.now().plus(ttl)));
        afterWrite();
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        if (key == null || key.isBlank() || type == null) {
            return Optional.empty();
        }
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            // 惰性清理：读到过期条目时顺手删掉
            store.remove(key, entry);
            return Optional.empty();
        }
        Object value = entry.value();
        if (!type.isInstance(value)) {
            // 类型不匹配按未命中处理，不抛异常：缓存里存着旧结构的数据是升级期的常态，
            // 让它表现为"未命中并回源"，而不是把请求打挂
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    @Override
    public void evict(String key) {
        if (key != null && !key.isBlank()) {
            store.remove(key);
        }
    }

    @Override
    public long increment(String key, long delta, Duration ttlWhenCreated) {
        requireKey(key);
        requireTtl(ttlWhenCreated);
        ensureCapacity();

        Instant now = Instant.now();
        // compute 在 ConcurrentHashMap 上对同一个键是原子的，并发自增不会丢计数
        Entry updated = store.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                // 不存在或已过期：重新起算，并设置存活时间
                return new Entry(delta, now.plus(ttlWhenCreated));
            }
            long current = existing.value() instanceof Number n ? n.longValue() : 0L;
            // 只自增不续期：续期会让持续的失败尝试把锁定窗口无限延长成永久锁定
            return new Entry(current + delta, existing.expiresAt());
        });
        afterWrite();
        return ((Number) updated.value()).longValue();
    }

    /** 当前条目数（含尚未清理的过期条目已被剔除），供测试与监控使用。 */
    public int size() {
        sweepExpired();
        return store.size();
    }

    private void afterWrite() {
        if (writeCount.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweepExpired();
        }
    }

    private void ensureCapacity() {
        if (store.size() < maxSize) {
            return;
        }
        sweepExpired();
        // 清理过期条目后仍然满，说明活跃数据确实超了上限：淘汰最接近过期的那条。
        // 用循环而非 if：并发写入下一次淘汰未必能腾出位置
        while (store.size() >= maxSize) {
            Map.Entry<String, Entry> earliest = store.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().expiresAt()))
                    .orElse(null);
            if (earliest == null) {
                return;
            }
            store.remove(earliest.getKey(), earliest.getValue());
        }
    }

    private void sweepExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("缓存键不能为空");
        }
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("缓存存活时间必须为正数，当前为: " + ttl);
        }
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
