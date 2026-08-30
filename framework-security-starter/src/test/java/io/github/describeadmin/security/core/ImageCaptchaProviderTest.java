package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.security.api.CaptchaChallenge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ImageCaptchaProvider} 的单元测试。
 *
 * <p>用真实的 {@link InMemoryCacheProvider}：本类正确性的一半落在"是否真的一次性"上，
 * 而"一次性"这条断言必须调用两次 {@code verify} 才能验证，mock 掉 cache 会失去意义。
 */
@DisplayName("图形字符验证码")
class ImageCaptchaProviderTest {

    private final CacheProvider cache = new InMemoryCacheProvider(100);

    private ImageCaptchaProvider provider() {
        return new ImageCaptchaProvider(cache, Duration.ofMinutes(2), 4);
    }

    @Test
    @DisplayName("生成的挑战 type 为 image，payload 带 data URI 格式的图片")
    void generateReturnsImageChallenge() {
        CaptchaChallenge challenge = provider().generate();

        assertThat(challenge.getCaptchaId()).isNotBlank();
        assertThat(challenge.getType()).isEqualTo(ImageCaptchaProvider.TYPE);
        assertThat(challenge.getPayload()).containsKey("image");
        assertThat((String) challenge.getPayload().get("image")).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("正确答案校验通过，大小写不敏感")
    void verifyPassesWithCorrectAnswerCaseInsensitive() {
        ImageCaptchaProvider provider = provider();
        CaptchaChallenge challenge = provider.generate();
        String code = cache.get(ImageCaptchaProvider.CACHE_KEY_PREFIX + challenge.getCaptchaId(), String.class)
                .orElseThrow();

        assertThat(provider.verify(challenge.getCaptchaId(), code.toLowerCase())).isTrue();
    }

    @Test
    @DisplayName("一次性：校验一次后即失效，同一 captchaId 用正确答案也无法再次通过")
    void verifyIsOneTimeOnly() {
        ImageCaptchaProvider provider = provider();
        CaptchaChallenge challenge = provider.generate();
        String code = cache.get(ImageCaptchaProvider.CACHE_KEY_PREFIX + challenge.getCaptchaId(), String.class)
                .orElseThrow();

        assertThat(provider.verify(challenge.getCaptchaId(), code)).isTrue();
        // 防重放的核心断言：同一 captchaId、同一正确答案，第二次必须失败
        assertThat(provider.verify(challenge.getCaptchaId(), code)).isFalse();
    }

    @Test
    @DisplayName("错误答案也会消费掉这次挑战")
    void verifyConsumesChallengeEvenWhenWrong() {
        ImageCaptchaProvider provider = provider();
        CaptchaChallenge challenge = provider.generate();
        String code = cache.get(ImageCaptchaProvider.CACHE_KEY_PREFIX + challenge.getCaptchaId(), String.class)
                .orElseThrow();

        assertThat(provider.verify(challenge.getCaptchaId(), "wrong")).isFalse();
        assertThat(provider.verify(challenge.getCaptchaId(), code)).isFalse();
    }

    @Test
    @DisplayName("不存在或已过期的 captchaId 返回 false，不抛异常")
    void verifyReturnsFalseForUnknownOrExpired() {
        ImageCaptchaProvider provider = provider();

        assertThat(provider.verify("no-such-id", "ABCD")).isFalse();
        assertThat(provider.verify(null, "ABCD")).isFalse();
    }

    @Test
    @DisplayName("TTL 到期后自动失效")
    void expiresAfterTtl() throws Exception {
        ImageCaptchaProvider provider = new ImageCaptchaProvider(cache, Duration.ofMillis(80), 4);
        CaptchaChallenge challenge = provider.generate();
        String code = cache.get(ImageCaptchaProvider.CACHE_KEY_PREFIX + challenge.getCaptchaId(), String.class)
                .orElseThrow();

        Thread.sleep(120);

        assertThat(provider.verify(challenge.getCaptchaId(), code)).isFalse();
    }

    @Test
    @DisplayName("生成的验证码不含易混淆字符 0/O/1/I/L")
    void generatedCodeExcludesConfusingCharacters() {
        ImageCaptchaProvider provider = provider();
        for (int i = 0; i < 50; i++) {
            CaptchaChallenge challenge = provider.generate();
            String code = cache.get(ImageCaptchaProvider.CACHE_KEY_PREFIX + challenge.getCaptchaId(), String.class)
                    .orElseThrow();
            assertThat(code).doesNotContainAnyWhitespaces();
            for (char c : code.toCharArray()) {
                assertThat("0O1IL").doesNotContain(String.valueOf(c));
            }
        }
    }

    @Test
    @DisplayName("非法构造参数在构造时就拒绝")
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ImageCaptchaProvider(null, Duration.ofMinutes(1), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageCaptchaProvider(cache, Duration.ZERO, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageCaptchaProvider(cache, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
