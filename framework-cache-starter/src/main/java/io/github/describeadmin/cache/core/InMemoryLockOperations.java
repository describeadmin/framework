package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.LockHandle;
import io.github.describeadmin.cache.api.LockOperations;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link LockOperations} 的进程内实现，框架默认值。
 *
 * <p><b>适用范围与已知局限（部署前必读）</b>：只在单个 JVM 内互斥。
 * <b>多实例部署时各实例各锁各的，互不感知——锁静默失效</b>。
 * 需要多实例互斥时引入 {@code framework-cache-redis-starter}，上层代码不用动。
 * 与 {@code InMemoryCacheProvider} 是同一组取舍。
 *
 * <p><b>实现说明</b>：
 * <ul>
 *   <li>加锁用 {@link ConcurrentHashMap#compute}——对同一键原子，并发下
 *       恰有一个线程放入自己的 entry，与 {@code InMemoryCacheProvider#increment}
 *       同一手法</li>
 *   <li>释放用 {@link ConcurrentHashMap#remove(Object, Object)} 按 entry
 *       （含随机 token）条件删除：TTL 过期后锁被他人抢走时，旧句柄的 close
 *       删不掉别人的 entry——token 安全与幂等由此同时成立</li>
 *   <li>{@link #lock} 的等待同样是轮询重试而不是 wait/notify 或
 *       {@code ReentrantLock}：保证与 Redis 实现行为一致，同一套语义、
 *       同一套测试可断言</li>
 *   <li>过期惰性清理（compute 时顺带判断），不起后台线程</li>
 * </ul>
 */
public class InMemoryLockOperations implements LockOperations {

    /** 等待策略的轮询间隔，与 Redis 实现保持一致。 */
    private static final long POLL_INTERVAL_MS = 100;

    /** 裸键超过该长度即截断（拼哈希后缀），防止用户输入构造出任意长的键。 */
    private static final int MAX_KEY_LENGTH = 128;

    private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final String keyPrefix;

    public InMemoryLockOperations(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("锁键前缀不能为空");
        }
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Optional<LockHandle> tryLock(String key, Duration ttl) {
        requireKey(key);
        requireTtl(ttl);
        String fullKey = fullKey(key);
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        LockEntry fresh = new LockEntry(token, now.plus(ttl));
        // compute 原子：并发下恰有一个线程把自己的 fresh 放进去并拿到它
        LockEntry winner = locks.compute(fullKey, (k, existing) ->
                existing == null || existing.expiresAt().isBefore(now) ? fresh : existing);
        if (winner != fresh) {
            return Optional.empty();
        }
        return Optional.of(new MemoryLockHandle(fullKey, fresh));
    }

    @Override
    public Optional<LockHandle> lock(String key, Duration ttl, Duration waitTimeout) {
        requirePositive(waitTimeout, "等待时间");
        long deadlineNanos = System.nanoTime() + waitTimeout.toNanos();
        while (true) {
            Optional<LockHandle> acquired = tryLock(key, ttl);
            if (acquired.isPresent()) {
                return acquired;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return Optional.empty();
            }
            try {
                long sleepMs = Math.min(POLL_INTERVAL_MS,
                        Math.max(1, remainingNanos / 1_000_000));
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                // 恢复中断位并放弃：中断说明有人要求尽快停下，继续自旋等待是错的
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待获取锁时被中断: " + key, e);
            }
        }
    }

    /** 仅供测试与监控观察当前持锁数。 */
    int size() {
        return locks.size();
    }

    private String fullKey(String key) {
        String normalized = key.length() <= MAX_KEY_LENGTH
                ? key
                : key.substring(0, MAX_KEY_LENGTH - 65) + "~" + sha256(key);
        return keyPrefix + normalized;
    }

    private record LockEntry(String token, Instant expiresAt) {
    }

    /** 必须是非静态内部类：close 需要访问外部实例的 {@code locks}。 */
    private final class MemoryLockHandle implements LockHandle {
        private final String fullKey;
        private final LockEntry entry;

        MemoryLockHandle(String fullKey, LockEntry entry) {
            this.fullKey = fullKey;
            this.entry = entry;
        }

        /**
         * 条件删除：只有存储里还是"我放进去的那条 entry"（同 token）才删。
         * 幂等：第二次 remove 时键已不在（或已是别人的 entry），均为空操作。
         */
        @Override
        public void close() {
            locks.remove(fullKey, entry);
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁键不能为空");
        }
    }

    private static void requireTtl(Duration ttl) {
        requirePositive(ttl, "锁存活时间");
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数，当前为: " + duration);
        }
    }

    /** 超长键的摘要后缀，与 Redis 实现的截断规则一致。 */
    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 规范保证 SHA-256 存在，走到这里只可能是 JVM 不合规
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
