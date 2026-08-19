package io.github.describeadmin.common.api;

/**
 * 当前登录用户 ID 的来源。
 *
 * <p>放在 framework-common 而非任何具体 starter，是为了让 <b>需要知道"谁在操作"的模块</b>
 * （审计字段填充、数据权限、操作日志）与 <b>知道"谁登录了"的模块</b>（framework-security-starter）
 * 彼此不直接依赖：只用 ORM 不用鉴权的业务方不应被迫把 Spring Security 拖进依赖树，
 * 反之只用 Web 层不用本框架 ORM 的业务方也不应被迫引入 MyBatis。
 *
 * <p>引入 framework-security-starter 时，由其自动注册一个读取 SecurityContext 的实现；
 * 未引入时保持 {@link #NOOP}，审计人字段留空，其余功能不受影响。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
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
