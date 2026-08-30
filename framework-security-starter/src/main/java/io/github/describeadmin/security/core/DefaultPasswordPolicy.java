package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.PasswordPolicy;

/**
 * {@link PasswordPolicy} 的默认实现：长度 + 字符类别数。
 *
 * <p>字符类别指大写字母、小写字母、数字、特殊字符（非字母数字）四类，
 * {@code minCharacterClasses} 要求至少覆盖其中几类——这是国内政务/等保场景
 * 常见的密码复杂度口径，默认 8 位 + 至少 3 类。
 */
public class DefaultPasswordPolicy implements PasswordPolicy {

    private final boolean enabled;
    private final int minLength;
    private final int minCharacterClasses;

    public DefaultPasswordPolicy(boolean enabled, int minLength, int minCharacterClasses) {
        this.enabled = enabled;
        this.minLength = minLength;
        this.minCharacterClasses = minCharacterClasses;
    }

    @Override
    public void validate(String rawPassword, String username) {
        if (!enabled) {
            return;
        }
        if (rawPassword == null || rawPassword.length() < minLength) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度不能少于" + minLength + "位");
        }
        int classes = 0;
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            classes++;
        }
        if (classes < minCharacterClasses) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "密码需同时包含大写字母、小写字母、数字、特殊字符中的至少" + minCharacterClasses + "类");
        }
        if (username != null && username.equalsIgnoreCase(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码不能与用户名相同");
        }
    }
}
