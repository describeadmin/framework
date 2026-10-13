package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.security.api.CaptchaChallenge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PoW 验证码")
class PowCaptchaProviderTest {

    private static final int MAX = 2_000;

    private final PowCaptchaProvider provider =
            new PowCaptchaProvider(new InMemoryCacheProvider(100), Duration.ofMinutes(2), MAX);

    /** 与前端求解器同一套算法：从 0 起逐个尝试，直到哈希相等。 */
    static long solve(Map<String, Object> payload) {
        String salt = (String) payload.get("salt");
        String challenge = (String) payload.get("challenge");
        int max = (Integer) payload.get("maxNumber");
        for (long n = 0; n <= max; n++) {
            if (PowCaptchaProvider.hash(salt, n).equals(challenge)) {
                return n;
            }
        }
        throw new AssertionError("在 maxNumber 内无解");
    }

    @Test
    @DisplayName("挑战形状：type=pow，payload 带算法、salt、challenge、maxNumber")
    void challengeShape() {
        CaptchaChallenge challenge = provider.generate();

        assertThat(challenge.getType()).isEqualTo("pow");
        assertThat(challenge.getPayload())
                .containsEntry("algorithm", "SHA-256")
                .containsEntry("maxNumber", MAX);
        assertThat((String) challenge.getPayload().get("salt")).matches("[0-9a-f]{32}");
        assertThat((String) challenge.getPayload().get("challenge")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("哈希口径：hex(SHA-256(salt + 十进制数字))，前端必须逐字节一致")
    void hashMatchesKnownVector() {
        // echo -n "abc123" | sha256sum
        assertThat(PowCaptchaProvider.hash("abc", 123))
                .isEqualTo("6ca13d52ca70c883e0f0bb101e425a89e8624de51db2d2392593af6a84118090");
    }

    @Test
    @DisplayName("正确解：通过")
    void acceptsCorrectSolution() {
        CaptchaChallenge challenge = provider.generate();
        long answer = solve(challenge.getPayload());

        assertThat(provider.verify(challenge.getCaptchaId(), String.valueOf(answer))).isTrue();
    }

    @Test
    @DisplayName("同一个解重放：第二次失败（一次性）")
    void rejectsReplay() {
        CaptchaChallenge challenge = provider.generate();
        String answer = String.valueOf(solve(challenge.getPayload()));

        assertThat(provider.verify(challenge.getCaptchaId(), answer)).isTrue();
        assertThat(provider.verify(challenge.getCaptchaId(), answer)).isFalse();
    }

    @Test
    @DisplayName("错解之后再交正确解：也失败（答错同样作废）")
    void wrongAnswerInvalidatesChallenge() {
        CaptchaChallenge challenge = provider.generate();
        long answer = solve(challenge.getPayload());
        String wrong = String.valueOf(answer == 0 ? 1 : answer - 1);

        assertThat(provider.verify(challenge.getCaptchaId(), wrong)).isFalse();
        assertThat(provider.verify(challenge.getCaptchaId(), String.valueOf(answer))).isFalse();
    }

    @Test
    @DisplayName("非数字、负数、越界、空值、未知 captchaId：一律 false，不抛异常")
    void rejectsMalformedAnswers() {
        for (String bad : new String[] {"abc", "-1", String.valueOf(MAX + 1), "", null, "1.5"}) {
            CaptchaChallenge challenge = provider.generate();
            assertThat(provider.verify(challenge.getCaptchaId(), bad)).as("answer=%s", bad).isFalse();
        }
        assertThat(provider.verify("no-such-id", "1")).isFalse();
        assertThat(provider.verify(null, "1")).isFalse();
        assertThat(provider.verify(" ", "1")).isFalse();
    }

    @Test
    @DisplayName("过期：失败")
    void rejectsExpired() throws InterruptedException {
        PowCaptchaProvider shortLived =
                new PowCaptchaProvider(new InMemoryCacheProvider(100), Duration.ofMillis(50), MAX);
        CaptchaChallenge challenge = shortLived.generate();
        long answer = solve(challenge.getPayload());
        Thread.sleep(120);

        assertThat(shortLived.verify(challenge.getCaptchaId(), String.valueOf(answer))).isFalse();
    }

    @Test
    @DisplayName("非法构造参数在构造时就拒绝")
    void rejectsInvalidConstruction() {
        InMemoryCacheProvider cache = new InMemoryCacheProvider(10);
        assertThatThrownBy(() -> new PowCaptchaProvider(null, Duration.ofMinutes(1), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PowCaptchaProvider(cache, Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PowCaptchaProvider(cache, Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
