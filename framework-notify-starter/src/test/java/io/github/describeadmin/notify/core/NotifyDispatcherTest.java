package io.github.describeadmin.notify.core;

import io.github.describeadmin.notify.api.NotifyChannel;
import io.github.describeadmin.notify.api.NotifyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotifyDispatcher} 的单元测试。
 */
@DisplayName("通知调度")
class NotifyDispatcherTest {

    private static NotifyChannel channel(String key, java.util.function.Consumer<NotifyMessage> onSend) {
        return new NotifyChannel() {
            @Override
            public String channel() {
                return key;
            }

            @Override
            public void send(NotifyMessage message) {
                onSend.accept(message);
            }
        };
    }

    @Nested
    @DisplayName("路由")
    class Routing {

        @Test
        @DisplayName("send 按 key 路由到对应渠道")
        void sendRoutesToMatchingChannelByKey() {
            List<NotifyMessage> logReceived = new java.util.ArrayList<>();
            List<NotifyMessage> smsReceived = new java.util.ArrayList<>();
            NotifyDispatcher dispatcher = new NotifyDispatcher(List.of(
                    channel("log", logReceived::add),
                    channel("sms", smsReceived::add)));

            NotifyMessage message = new NotifyMessage("标题", "正文", List.of("u1"));
            dispatcher.send("sms", message);

            assertThat(smsReceived).containsExactly(message);
            assertThat(logReceived).isEmpty();
        }

        @Test
        @DisplayName("send 到未注册的渠道抛异常，异常信息包含尝试的 key")
        void sendToUnknownChannelThrows() {
            NotifyDispatcher dispatcher = new NotifyDispatcher(List.of(channel("log", m -> {})));

            assertThatThrownBy(() -> dispatcher.send("dingtalk", new NotifyMessage(null, "x", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dingtalk");
        }
    }

    @Nested
    @DisplayName("广播")
    class Broadcast {

        @Test
        @DisplayName("broadcast 触达全部已注册渠道")
        void broadcastInvokesAllRegisteredChannels() {
            AtomicInteger count = new AtomicInteger();
            NotifyDispatcher dispatcher = new NotifyDispatcher(List.of(
                    channel("log", m -> count.incrementAndGet()),
                    channel("sms", m -> count.incrementAndGet())));

            dispatcher.broadcast(new NotifyMessage(null, "x", null));

            assertThat(count).hasValue(2);
        }

        @Test
        @DisplayName("没有任何渠道时 broadcast 静默返回")
        void broadcastWithNoChannelsIsNoop() {
            NotifyDispatcher dispatcher = new NotifyDispatcher(List.of());

            dispatcher.broadcast(new NotifyMessage(null, "x", null));
            // 未抛出即视为通过
        }
    }

    @Nested
    @DisplayName("重复通道标识")
    class DuplicateChannel {

        @Test
        @DisplayName("构造期发现重复标识立即抛异常")
        void constructorThrowsOnDuplicateChannelKey() {
            assertThatThrownBy(() -> new NotifyDispatcher(List.of(
                    channel("dingtalk", m -> {}),
                    channel("dingtalk", m -> {}))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dingtalk");
        }

        @Test
        @DisplayName("构造期发现空白标识立即抛异常")
        void constructorThrowsWhenChannelKeyIsBlank() {
            assertThatThrownBy(() -> new NotifyDispatcher(List.of(channel("  ", m -> {}))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("并发")
    class Concurrency {

        @Test
        @DisplayName("并发 send 调用不丢失")
        void concurrentSendCallsDoNotLoseInvocations() throws Exception {
            AtomicInteger received = new AtomicInteger();
            NotifyDispatcher dispatcher = new NotifyDispatcher(List.of(channel("log", m -> received.incrementAndGet())));

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
                            dispatcher.send("log", new NotifyMessage(null, "x", null));
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
            assertThat(received).hasValue(threads * perThread);
        }
    }
}
