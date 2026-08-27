package io.github.describeadmin.system.core;

import io.github.describeadmin.security.core.DefaultPasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * {@link RandomPasswordGenerator} 的单元测试。dev-seed 的口令强度全靠它。
 */
@DisplayName("随机口令生成器")
class RandomPasswordGeneratorTest {

    /** 与框架默认口径一致：8 位 + 至少 3 类字符。 */
    private final DefaultPasswordPolicy policy = new DefaultPasswordPolicy(true, 8, 3);

    @RepeatedTest(50)
    @DisplayName("每次都产出满足 PasswordPolicy 的口令")
    void generatesPolicyCompliantPassword() {
        String pwd = RandomPasswordGenerator.generate(policy, "admin");
        assertThat(pwd).hasSize(16);
        assertThatNoException().isThrownBy(() -> policy.validate(pwd, "admin"));
    }

    @Test
    @DisplayName("连续多次生成互不相同")
    void generatesDistinctValues() {
        String a = RandomPasswordGenerator.generate(policy, "admin");
        String b = RandomPasswordGenerator.generate(policy, "admin");
        assertThat(a).isNotEqualTo(b);
    }
}
