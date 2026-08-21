package io.github.describeadmin.storage.core;

import io.github.describeadmin.storage.api.StorageException;
import io.github.describeadmin.storage.api.StorageObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalFileStorageProvider} 的单元测试。
 */
@DisplayName("本地文件存储")
class LocalFileStorageProviderTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageProvider newProvider() {
        return new LocalFileStorageProvider(tempDir.toString(), "/storage/", true);
    }

    private static InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("写入与读取")
    class ReadWrite {

        private LocalFileStorageProvider storage;

        @BeforeEach
        void setUp() {
            storage = newProvider();
        }

        @Test
        @DisplayName("写入后可读出相同内容")
        void putThenGetReturnsSameBytes() throws Exception {
            storage.put("a/b.txt", content("中文内容"), 0, "text/plain");

            try (InputStream in = storage.get("a/b.txt").orElseThrow()) {
                // 值断言而非存在性断言：编码坏掉时文件照样"存在"（CLAUDE.md 3.6）
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("中文内容");
            }
        }

        @Test
        @DisplayName("StorageObject.size 以磁盘实际字节数为准，不信任调用方传入值")
        void putReturnsStorageObjectWithActualDiskSize() {
            StorageObject object = storage.put("k", content("12345"), 999, "text/plain");

            assertThat(object.size()).isEqualTo(5L);
        }

        @Test
        @DisplayName("url 使用配置的前缀拼接 key")
        void urlUsesConfiguredPrefix() {
            assertThat(storage.url("a/b.txt")).isEqualTo("/storage/a/b.txt");
        }

        @Test
        @DisplayName("本地实现没有签名概念，presignedUrl 退化为 url，忽略过期时间")
        void presignedUrlFallsBackToUrl() {
            assertThat(storage.presignedUrl("a/b.txt", Duration.ofMinutes(10)))
                    .isEqualTo(storage.url("a/b.txt"));
        }

        @Test
        @DisplayName("未写入的 key 读取返回空，而不是异常")
        void getMissingKeyReturnsEmpty() {
            assertThat(storage.get("never-written")).isEmpty();
        }

        @Test
        @DisplayName("exists 随 put/remove 变化")
        void existsReflectsPutAndRemove() {
            assertThat(storage.exists("k")).isFalse();

            storage.put("k", content("v"), 0, null);
            assertThat(storage.exists("k")).isTrue();

            storage.remove("k");
            assertThat(storage.exists("k")).isFalse();
        }

        @Test
        @DisplayName("删除不存在的 key 静默返回")
        void removeMissingKeyIsNoop() {
            storage.remove("never-written");
            // 未抛出即视为通过
        }
    }

    @Nested
    @DisplayName("覆盖语义")
    class Overwrite {

        @Test
        @DisplayName("启用覆盖时重复写入替换旧内容")
        void putOverwritesWhenOverwriteEnabled() throws Exception {
            LocalFileStorageProvider storage = new LocalFileStorageProvider(tempDir.toString(), "/storage/", true);

            storage.put("k", content("old"), 0, null);
            storage.put("k", content("new"), 0, null);

            try (InputStream in = storage.get("k").orElseThrow()) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("new");
            }
        }

        @Test
        @DisplayName("关闭覆盖后对已存在的 key 再次写入抛异常")
        void putRejectsExistingKeyWhenOverwriteDisabled() {
            LocalFileStorageProvider storage = new LocalFileStorageProvider(tempDir.toString(), "/storage/", false);
            storage.put("k", content("old"), 0, null);

            assertThatThrownBy(() -> storage.put("k", content("new"), 0, null))
                    .isInstanceOf(StorageException.class);
        }
    }

    @Nested
    @DisplayName("路径穿越防护")
    class PathTraversal {

        private LocalFileStorageProvider storage;

        @BeforeEach
        void setUp() {
            storage = newProvider();
        }

        static Stream<String> maliciousKeys() {
            return Stream.of("../secret.txt", "a/../../secret.txt", "/etc/passwd",
                    "a\\..\\secret.txt", "C:/windows/win.ini");
        }

        @Test
        @DisplayName("写方法拒绝含 .. 片段的 key")
        void rejectsKeyWithDotDotSegment() {
            assertThatThrownBy(() -> storage.put("../secret.txt", content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("写方法拒绝以 / 开头的 key")
        void rejectsKeyWithLeadingSlash() {
            assertThatThrownBy(() -> storage.put("/etc/passwd", content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("写方法拒绝含反斜杠的 key")
        void rejectsKeyWithBackslash() {
            assertThatThrownBy(() -> storage.put("a\\..\\secret.txt", content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("写方法拒绝带盘符的 key")
        void rejectsWindowsDriveLetterKey() {
            assertThatThrownBy(() -> storage.put("C:/windows/win.ini", content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("写方法拒绝空白或 null 的 key")
        void rejectsBlankOrNullKeyOnWrite() {
            assertThatThrownBy(() -> storage.put("", content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.put(null, content("x"), 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.remove(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("读方法对同一批非法 key 按未命中处理，不抛异常")
        void readMethodsTreatInvalidKeyAsMiss() {
            maliciousKeys().forEach(key -> {
                assertThat(storage.get(key)).as("get(%s)", key).isEmpty();
                assertThat(storage.exists(key)).as("exists(%s)", key).isFalse();
            });
            assertThat(storage.get(null)).isEmpty();
            assertThat(storage.exists(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("并发")
    class Concurrency {

        @Test
        @DisplayName("并发写入不同 key 到同一未存在子目录下不出错")
        void concurrentPutsToDistinctKeysUnderNewSubdirectorySucceed() throws Exception {
            LocalFileStorageProvider storage = newProvider();
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger failures = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        storage.put("shared-subdir/file-" + index + ".txt", content("v" + index), 0, null);
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
            for (int i = 0; i < threads; i++) {
                assertThat(storage.exists("shared-subdir/file-" + i + ".txt")).isTrue();
            }
        }
    }
}
