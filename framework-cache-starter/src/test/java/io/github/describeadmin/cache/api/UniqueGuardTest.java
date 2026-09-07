package io.github.describeadmin.cache.api;

import io.github.describeadmin.cache.core.InMemoryLockOperations;
import io.github.describeadmin.common.api.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UniqueGuard} 的单元测试。
 *
 * <p>核心用例是"check-then-insert 竞态被消除"：两个并发调用带同一唯一值，
 * 一个完成业务、一个收到"操作冲突"。这正是它存在的理由。
 */
@DisplayName("键级唯一性保护")
class UniqueGuardTest {

    private InMemoryLockOperations locks;
    private UniqueGuard guard;

    @BeforeEach
    void setUp() {
        locks = new InMemoryLockOperations("describeadmin:lock:");
        guard = new UniqueGuard(locks);
    }

    @Nested
    @DisplayName("基本行为")
    class Basics {

        @Test
        @DisplayName("正常执行：action 的返回值原样透传")
        void passesThroughReturnValue() {
            String result = guard.execute("user:username", "jeff", () -> "created");

            assertThat(result).isEqualTo("created");
        }

        @Test
        @DisplayName("值被占用：立即抛\"操作冲突\"，action 不执行")
        void conflictThrowsWithoutExecuting() {
            assertThat(locks.tryLock("unique:user:username:jeff", java.time.Duration.ofSeconds(30)))
                    .isPresent();

            AtomicInteger executions = new AtomicInteger();
            assertThatThrownBy(() -> guard.execute("user:username", "jeff", () -> executions.incrementAndGet()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作冲突");
            assertThat(executions.get()).isZero();
        }

        @Test
        @DisplayName("不同值互不阻塞：键级粒度")
        void differentValuesDoNotBlockEachOther() {
            guard.execute("user:username", "jeff", () -> "first");

            assertThat(guard.execute("user:username", "bob", () -> "second")).isEqualTo("second");
        }

        @Test
        @DisplayName("action 抛异常时锁自动释放")
        void lockReleasedWhenActionThrows() {
            assertThatThrownBy(() -> guard.execute("user:username", "jeff", () -> {
                throw new IllegalStateException("校验失败");
            })).isInstanceOf(IllegalStateException.class);

            // 下一次同值调用必须能拿到锁——否则一次失败会占用该值直到 TTL 过期
            assertThat(guard.execute("user:username", "jeff", () -> "retry")).isEqualTo("retry");
        }

        @Test
        @DisplayName("空值不加锁直接执行：空值没有唯一性冲突可言")
        void blankValueSkipsLocking() {
            assertThat(guard.execute("user:mobile", null, () -> "a")).isEqualTo("a");
            assertThat(guard.execute("user:mobile", "  ", () -> "b")).isEqualTo("b");
        }

        @Test
        @DisplayName("嵌套不同键：各占一把锁，正常执行（多字段唯一性的正当用法）")
        void nestedDifferentKeysWork() {
            String result = guard.execute("user:username", "jeff", () ->
                    guard.execute("user:mobile", "13800000000", () ->
                            guard.execute("user:email", "jeff@example.com", () -> "created")));

            assertThat(result).isEqualTo("created");
        }

        @Test
        @DisplayName("⚠️ 嵌套同键必然自锁死：锁不可重入，单线程也 100% 抛\"操作冲突\"")
        void nestedSameKeyDeadlocksItself() {
            // 这不是并发场景——单线程、无争用，照样失败。写这条用例是因为它曾真实发生过：
            // "入口方法持锁" + "锁内调用的校验方法自己也持同一把锁"，
            // 表现为凡是带该字段的请求全部报"操作冲突，请稍后重试"，看起来却像是并发问题。
            // 修法是二选一持锁，不是把锁改成可重入（不可重入是 LockOperations 的刻意取舍）
            AtomicInteger innerRuns = new AtomicInteger();

            assertThatThrownBy(() -> guard.execute("user:mobile", "13800000000", () ->
                    guard.execute("user:mobile", "13800000000", innerRuns::incrementAndGet)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("操作冲突");

            assertThat(innerRuns.get()).as("内层 action 根本没机会执行").isZero();
            // 外层的 try-with-resources 仍正常释放：下一次同键调用能拿到锁
            assertThat(guard.execute("user:mobile", "13800000000", () -> "retry")).isEqualTo("retry");
        }
    }

    @Nested
    @DisplayName("并发唯一性（本组件的存在理由）")
    class Concurrency {

        @Test
        @DisplayName("两个并发调用同一唯一值：恰一个完成业务，另一个收到操作冲突")
        void exactlyOneWins() throws Exception {
            // 竞态模型：两线程同时到达 check 点（屏障对齐），业务段持锁 ~150ms
            // 保证争用真实发生。无论谁赢，断言的都是"恰好一个完成、一个被挡"
            java.util.concurrent.CyclicBarrier startLine = new java.util.concurrent.CyclicBarrier(2);
            AtomicInteger businessRuns = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch done = new CountDownLatch(2);
            try {
                for (int i = 0; i < 2; i++) {
                    executor.submit(() -> {
                        try {
                            startLine.await();
                            guard.execute("user:username", "jeff", () -> {
                                try {
                                    // 模拟"检查通过后、写入前"的耗时窗口：
                                    // 没有锁时两个线程都会走过这里——重复插入正是要修的漏洞
                                    Thread.sleep(150);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                businessRuns.incrementAndGet();
                                return "done";
                            });
                        } catch (Exception conflict) {
                            conflicts.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(businessRuns.get())
                    .as("没有锁时这里是 2——重复插入正是要修的漏洞")
                    .isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(1);
        }
    }
}
