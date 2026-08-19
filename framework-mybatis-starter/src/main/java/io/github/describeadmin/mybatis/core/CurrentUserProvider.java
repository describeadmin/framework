package io.github.describeadmin.mybatis.core;

/**
 * 当前登录用户 ID 的来源。
 *
 * <p>存在的意义是让 framework-mybatis-starter <b>不依赖</b> framework-security-starter：
 * 只用 ORM 不用鉴权的业务方，不应被迫把 Spring Security 拖进依赖树。
 * 引入了 security-starter 时，由其提供一个基于 SecurityContext 的实现覆盖默认值。
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /** 无鉴权上下文时的默认实现，审计人字段留空。 */
    CurrentUserProvider NOOP = () -> null;

    /**
     * @return 当前登录用户 ID；无登录上下文时返回 {@code null}
     */
    Long currentUserId();
}
