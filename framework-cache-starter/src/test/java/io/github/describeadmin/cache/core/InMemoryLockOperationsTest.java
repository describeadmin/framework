package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.LockHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryLockOperations} 的单元测试。
 *
 * <p>重点压三条最容易做错的性质：并发下恰有一个赢家、TTL 过期后锁开放、
 * 旧句柄不能误删别人的锁（token 安全）。这三条分别对应它的三个真实使用场景：
 * 接口防重、持有者宕机自愈、超长持锁。
 */
@DisplayName("进程内锁")
class InMemoryLockOperationsTest {

    private final InMemoryLockOperations locks = new InMemoryLockOperations("describeadmin:lock:");

    @Nested
    @DisplayName("tryLock")
    class TryLock {

        @Test
        @DisplayName("先到的拿到，后到的空手而归")
        void secondAcquirerGetsEmpty() {
            Optional<LockHandle> first = locks.tryLock("user:1", Duration.ofSeconds(10));
            Optional<LockHandle> second = locks.tryLock("user:1", Duration.ofSeconds(10));

            assertThat(first).isPresent();
            assertThat(second).isEmpty();
        }

        @Test
        @DisplayName("不同键互不阻塞")
        void differentKeysDoNotBlockEachOther() {
            assertThat(locks.tryLock("user:1", Duration.ofSeconds(10))).isPresent();
            assertThat(locks.tryLock("user:2", Duration.ofSeconds(10))).isPresent();
        }

        @Test
        @DisplayName("释放后可重新获取")
        void reAcquireAfterRelease() {
            Optional<LockHandle> first = locks.tryLock("user:1", Duration.ofSeconds(10));
            assertThat(first).isPresent();
            first.get().close();

            assertThat(locks.tryLock("user:1", Duration.ofSeconds(10))).isPresent();
        }

        @Test
        @DisplayName("close 幂等：重复释放安全")
        void closeIsIdempotent() {
            LockHandle handle = locks.tryLock("user:1", Duration.ofSeconds(10)).orElseThrow();
            handle.close();
            handle.close();

            // 两次 close 之后锁确实没了，且没有误伤后续获取
            assertThat(locks.tryLock("user:1", Duration.ofSeconds(10))).isPresent();
        }

        @Test
        @DisplayName("参数校验：空键与非正 TTL 直接拒绝")
        void rejectsInvalidArguments() {
            assertThatThrownBy(() -> locks.tryLock(" ", Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> locks.tryLock("k", Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> locks.tryLock("k", Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("键前缀为空直接拒绝，而不是默默产出无前缀的键")
        void rejectsBlankPrefix() {
            assertThatThrownBy(() -> new InMemoryLockOperations(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("TTL 与 token 安全")
    class TtlAndTokenSafety {

        @Test
        @DisplayName("TTL 过期后锁自动开放")
        void lockExpiresAfterTtl() throws Exception {
            assertThat(locks.tryLock("user:1", Duration.ofMillis(50))).isPresent();
            Thread.sleep(80);

            assertThat(locks.tryLock("user:1", Duration.ofSeconds(10))).isPresent();
        }

        @Test
        @DisplayName("过期后旧句柄的 close 不能误删新持有者的锁")
        void staleHandleCannotReleaseOthersLock() throws Exception {
            // 场景：T1 拿锁后处理超时（TTL 已过），T2 抢到锁；此时 T1 的 finally 执行 close
            LockHandle stale = locks.tryLock("user:1", Duration.ofMillis(50)).orElseThrow();
            Thread.sleep(80);
            LockHandle current = locks.tryLock("user:1", Duration.ofSeconds(30)).orElseThrow();

            stale.close();

            // T2 的锁必须还在
            assertThat(locks.tryLock("user:1", Duration.ofSeconds(10))).isEmpty();
            current.close();
        }
    }

    @Nested
    @DisplayName("lock（限时等待）")
    class WaitLock {

        @Test
        @DisplayName("锁被占时等待，释放后获取成功")
        void waitsUntilReleased() throws Exception {
            LockHandle held = locks.tryLock("user:1", Duration.ofSeconds(30)).orElseThrow();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    held.close();
                });
                Optional<LockHandle> acquired = locks.lock("user:1", Duration.ofSeconds(10), Duration.ofSeconds(5));
                assertThat(acquired).isPresent();
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("等待超时返回空，而不是死等")
        void timeoutReturnsEmpty() {
            assertThat(locks.tryLock("user:1", Duration.ofSeconds(30))).isPresent();

            long start = System.currentTimeMillis();
            Optional<LockHandle> acquired = locks.lock("user:1", Duration.ofSeconds(30), Duration.ofMillis(300));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(acquired).isEmpty();
            // 超时要被遵守（留轮询间隔的余量），否则等待会变成事实上的无限期
            assertThat(elapsed).isLessThan(2000);
        }

        @Test
        @DisplayName("等待中被中断：抛异常并恢复中断位")
        void interruptionRestoresFlagAndThrows() throws Exception {
            assertThat(locks.tryLock("user:1", Duration.ofSeconds(30))).isPresent();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicInteger flagSeen = new AtomicInteger(-1);
            Thread waiter = new Thread(() -> {
                try {
                    started.countDown();
                    locks.lock("user:1", Duration.ofSeconds(30), Duration.ofSeconds(30));
                } catch (IllegalStateException expected) {
                    flagSeen.set(Thread.currentThread().isInterrupted() ? 1 : 0);
                } finally {
                    finished.countDown();
                }
            });
            waiter.start();
            started.await();
            Thread.sleep(150);
            waiter.interrupt();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(flagSeen.get())
                    .as("抛出异常时中断位必须已恢复，否则上层丢失中断信号")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("超长键截断")
    class LongKey {

        @Test
        @DisplayName("超过阈值的键被截断但不碰撞：不同长键仍是不同的锁")
        void longKeysDoNotCollide() {
            String base = "u:".repeat(60);
            String keyA = base + "AAAA";
            String keyB = base + "BBBB";

            assertThat(locks.tryLock(keyA, Duration.ofSeconds(10))).isPresent();
            assertThat(locks.tryLock(keyB, Duration.ofSeconds(10)))
                    .as("截断若丢失区分度，两个不同的键会互相阻塞")
                    .isPresent();
        }

        @Test
        @DisplayName("同一长键重复获取仍是同一把锁")
        void sameLongKeyIsSameLock() {
            String longKey = "x:".repeat(100) + "tail";
            assertThat(locks.tryLock(longKey, Duration.ofSeconds(10))).isPresent();
            assertThat(locks.tryLock(longKey, Duration.ofSeconds(10))).isEmpty();
        }
    }

    @Nested
    @DisplayName("并发原子性")
    class Concurrency {

        @Test
        @DisplayName("N 个线程抢同一把锁，恰好一个成功")
        void exactlyOneWinner() throws Exception {
            int threads = 16;
            AtomicInteger winners = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch done = new CountDownLatch(threads);
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                IntStream.range(0, threads).forEach(i -> executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (locks.tryLock("user:hot", Duration.ofSeconds(30)).isPresent()) {
                            winners.incrementAndGet();
                        }
                    } finally {
                        done.countDown();
                    }
                }));
                ready.await();
                done.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertThat(winners.get())
                    .as("compute 原子性被破坏时，这里会出现多于一个赢家")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("抢完即释放的循环：N 线程各抢 M 次，全程无双重持有")
        void acquireReleaseLoop() throws Exception {
            int threads = 8;
            int perThread = 50;
            AtomicInteger violations = new AtomicInteger();
            AtomicInteger held = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch done = new CountDownLatch(threads);
            try {
                List<Runnable> tasks = IntStream.range(0, threads).mapToObj(i -> (Runnable) () -> {
                    try {
                        for (int j = 0; j < perThread; j++) {
                            Optional<LockHandle> handle = locks.tryLock("user:loop", Duration.ofSeconds(30));
                            if (handle.isPresent()) {
                                if (held.incrementAndGet() > 1) {
                                    violations.incrementAndGet();
                                }
                                held.decrementAndGet();
                                handle.get().close();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                }).toList();
                tasks.forEach(executor::submit);
                assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(violations.get())
                    .as("任何时刻至多一个持有者，违反即互斥被破坏")
                    .isZero();
        }
    }
}
