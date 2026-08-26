package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultPasswordPolicy} 的单元测试。
 *
 * <p>默认口径：至少 8 位，且大写字母/小写字母/数字/特殊字符四类中至少覆盖 3 类。
 */
@DisplayName("默认密码复杂度策略")
class DefaultPasswordPolicyTest {

    private final DefaultPasswordPolicy policy = new DefaultPasswordPolicy(true, 8, 3);

    @Test
    @DisplayName("长度不足被拒绝")
    void rejectsTooShort() {
        assertThatThrownBy(() -> policy.validate("Ab1!", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("长度");
    }

    @Test
    @DisplayName("只有两类字符（小写字母+数字）被拒绝")
    void rejectsTooFewCharacterClasses() {
        assertThatThrownBy(() -> policy.validate("abcdefg1", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类");
    }

    @Test
    @DisplayName("覆盖满 3 类字符（大写+小写+数字）且长度达标则通过")
    void acceptsThreeCharacterClasses() {
        assertThatNoException().isThrownBy(() -> policy.validate("Abcdefg1", null));
    }

    @Test
    @DisplayName("连字符等符号计为特殊字符一类：小写+数字+连字符也算满 3 类")
    void hyphenCountsAsSpecialCharacterClass() {
        assertThatNoException().isThrownBy(() -> policy.validate("pwd-123456", null));
    }

    @Test
    @DisplayName("密码与用户名相同（忽略大小写）被拒绝")
    void rejectsPasswordEqualToUsername() {
        assertThatThrownBy(() -> policy.validate("Zhang-San1", "Zhang-San1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名");
    }

    @Test
    @DisplayName("username 为 null 时不做同名校验")
    void skipsUsernameCheckWhenUsernameIsNull() {
        assertThatNoException().isThrownBy(() -> policy.validate("Abcdefg1", null));
    }

    @Test
    @DisplayName("关闭策略后不做任何校验，弱密码也放行")
    void disabledPolicySkipsAllChecks() {
        DefaultPasswordPolicy disabled = new DefaultPasswordPolicy(false, 8, 3);
        assertThatNoException().isThrownBy(() -> disabled.validate("123", null));
    }
}
