package io.github.describeadmin.cache.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryCacheProvider} 的单元测试。
 */
@DisplayName("内存缓存")
class InMemoryCacheProviderTest {

    private final InMemoryCacheProvider cache = new InMemoryCacheProvider(1000);

    @Nested
    @DisplayName("读写")
    class ReadWrite {

        @Test
        @DisplayName("写入后可按原类型读出")
        void putThenGet() {
            cache.put("k", "中文值", Duration.ofMinutes(1));

            // 值断言而非存在性断言：编码坏掉时字段照样"存在"（CLAUDE.md 3.6）
            assertThat(cache.get("k", String.class)).contains("中文值");
        }

        @Test
        @DisplayName("重复写入覆盖旧值")
        void putOverwrites() {
            cache.put("k", "old", Duration.ofMinutes(1));
            cache.put("k", "new", Duration.ofMinutes(1));

            assertThat(cache.get("k", String.class)).contains("new");
        }

        @Test
        @DisplayName("未命中返回空，而不是 null 或异常")
        void missReturnsEmpty() {
            assertThat(cache.get("never-written", String.class)).isEmpty();
        }

        @Test
        @DisplayName("类型不匹配按未命中处理，不抛类型转换异常")
        void typeMismatchIsMiss() {
            cache.put("k", "不是数字", Duration.ofMinutes(1));

            // 升级期缓存里存着旧结构的数据是常态，应表现为"未命中并回源"而不是把请求打挂
            assertThat(cache.get("k", Long.class)).isEmpty();
        }

        @Test
        @DisplayName("过期后读不出")
        void expiredIsMiss() throws Exception {
            cache.put("k", "v", Duration.ofMillis(30));
            Thread.sleep(60);

            assertThat(cache.get("k", String.class)).isEmpty();
        }

        @Test
        @DisplayName("写入 null 等同于删除")
        void putNullEvicts() {
            cache.put("k", "v", Duration.ofMinutes(1));
            cache.put("k", null, Duration.ofMinutes(1));

            assertThat(cache.get("k", String.class)).isEmpty();
        }

        @Test
        @DisplayName("非法的键与存活时间直接拒绝，不静默接受")
        void rejectsInvalidArguments() {
            assertThatThrownBy(() -> cache.put("", "v", Duration.ofMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cache.put("k", "v", Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cache.put("k", "v", Duration.ofMinutes(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("自增")
    class Increment {

        @Test
        @DisplayName("键不存在时从 0 起算")
        void startsFromZero() {
            assertThat(cache.increment("c", 1, Duration.ofMinutes(1))).isEqualTo(1);
            assertThat(cache.increment("c", 1, Duration.ofMinutes(1))).isEqualTo(2);
        }

        @Test
        @DisplayName("自增结果可被 get 读出，两个方法共用同一份数据")
        void visibleThroughGet() {
            cache.increment("c", 3, Duration.ofMinutes(1));

            // 这条不成立的话，LoginAttemptGuard 的"记失败用 increment、判锁定用 get"就是错的
            assertThat(cache.get("c", Long.class)).contains(3L);
        }

        @Test
        @DisplayName("已存在的键只自增、不续期")
        void doesNotRenewTtl() throws Exception {
            cache.increment("c", 1, Duration.ofMillis(80));
            Thread.sleep(40);
            cache.increment("c", 1, Duration.ofMillis(80));
            Thread.sleep(60);

            // 若第二次自增把存活时间重置了，这里还会读到值——
            // 那意味着持续的失败尝试会把锁定窗口无限延长成永久锁定
            assertThat(cache.get("c", Long.class)).isEmpty();
        }

        @Test
        @DisplayName("并发自增不丢计数")
        void isAtomic() throws Exception {
            int threads = 8;
            int perThread = 500;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger failures = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            cache.increment("hot", 1, Duration.ofMinutes(1));
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(failures).hasValue(0);
            // get-then-put 的写法在这里会丢计数，而丢计数意味着爆破防护被绕过
            assertThat(cache.get("hot", Long.class)).contains((long) threads * perThread);
        }
    }

    @Nested
    @DisplayName("容量上限")
    class Capacity {

        @Test
        @DisplayName("超过上限后淘汰最接近过期的条目，而不是无限增长")
        void evictsWhenFull() {
            InMemoryCacheProvider small = new InMemoryCacheProvider(3);
            // 存活时间递增，因此 early 最接近过期
            small.put("early", "v", Duration.ofSeconds(10));
            small.put("mid", "v", Duration.ofSeconds(60));
            small.put("late", "v", Duration.ofSeconds(600));

            small.put("newcomer", "v", Duration.ofSeconds(600));

            assertThat(small.size()).isLessThanOrEqualTo(3);
            assertThat(small.get("early", String.class)).as("最接近过期的应被淘汰").isEmpty();
            assertThat(small.get("newcomer", String.class)).contains("v");
        }

        @Test
        @DisplayName("非法容量在构造时就拒绝")
        void rejectsInvalidCapacity() {
            assertThatThrownBy(() -> new InMemoryCacheProvider(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("按前缀枚举")
    class KeysWithPrefix {

        @Test
        @DisplayName("只返回匹配前缀的 key")
        void onlyMatchingPrefixReturned() {
            cache.put("describeadmin:login:fail:alice", 1L, Duration.ofMinutes(1));
            cache.put("describeadmin:login:fail:bob", 1L, Duration.ofMinutes(1));
            cache.put("describeadmin:other:thing", 1L, Duration.ofMinutes(1));

            assertThat(cache.keysWithPrefix("describeadmin:login:fail:"))
                    .containsExactlyInAnyOrder(
                            "describeadmin:login:fail:alice", "describeadmin:login:fail:bob");
        }

        @Test
        @DisplayName("已过期的 key 不出现在结果里")
        void expiredKeysAreExcluded() throws Exception {
            cache.put("describeadmin:login:fail:alice", 1L, Duration.ofMillis(30));
            Thread.sleep(60);

            assertThat(cache.keysWithPrefix("describeadmin:login:fail:")).isEmpty();
        }

        @Test
        @DisplayName("没有匹配项时返回空集合，不返回 null")
        void noMatchReturnsEmptySet() {
            assertThat(cache.keysWithPrefix("no-such-prefix:")).isEmpty();
        }
    }
}
