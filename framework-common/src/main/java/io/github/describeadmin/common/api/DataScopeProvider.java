package io.github.describeadmin.common.api;

import java.util.Optional;

/**
 * 当前登录用户的数据权限上下文来源。
 *
 * <p>与 {@link CurrentUserProvider} 同一个用意：让需要做行级数据过滤的模块
 * （framework-mybatis-starter 的数据权限拦截器）与知道"当前用户是谁、范围多大"的模块
 * （framework-security-starter）彼此不直接依赖。只用 ORM 不用鉴权的业务方不应被迫把
 * Spring Security 拖进依赖树。
 *
 * <p>引入 framework-security-starter 时，由其自动注册一个读取 SecurityContext 的实现；
 * 未引入时保持 {@link #NOOP}，数据权限拦截器视为"不过滤"——此时应用本来就没有认证，
 * 也没有数据可依据做过滤。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
@FunctionalInterface
public interface DataScopeProvider {

    /** 无鉴权上下文时的默认实现，等同于未启用数据权限。 */
    DataScopeProvider NOOP = Optional::empty;

    /**
     * @return 当前登录用户的数据权限上下文；无登录上下文时返回空
     */
    Optional<DataScopeContext> current();
}
