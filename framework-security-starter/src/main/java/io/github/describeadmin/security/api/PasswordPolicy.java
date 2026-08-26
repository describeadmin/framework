package io.github.describeadmin.security.api;

/**
 * 密码复杂度策略。
 *
 * <p>覆盖框架内全部"设置密码"的入口：自助改密、管理员重置密码、创建用户设初始密码——
 * 三处共用同一份规则，避免系统内出现绕过策略设置的弱密码。
 *
 * <p>核心提供零依赖的默认实现 {@code DefaultPasswordPolicy}（长度 + 字符类别数），
 * 业务方注册自己的 {@link PasswordPolicy} Bean 即可覆盖（如接入字典库黑名单、
 * 对接 HaveIBeenPwned 等更强校验），与 {@link TokenStore} 是同一模式。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface PasswordPolicy {

    /**
     * 校验密码是否满足复杂度要求，不满足时抛出
     * {@code BizException(ResultCode.BAD_REQUEST, ...)}。
     *
     * @param rawPassword 明文密码
     * @param username    所属用户名，用于"密码不能与用户名相同"这类校验；不需要该项时可传 null
     */
    void validate(String rawPassword, String username);
}
