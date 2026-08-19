package io.github.describeadmin.security.api;

import java.util.Optional;

/**
 * 用户加载 SPI。
 *
 * <p>框架不规定用户存在哪里、表结构长什么样——业务方实现本接口即可。
 * 这样 framework-security-starter <b>不依赖</b> framework-mybatis-starter，
 * 只用鉴权不用本框架 ORM 的业务方（例如用户数据来自统一认证中心）不会被拖进 ORM 依赖。
 *
 * <p>框架提供了 {@code sys_user} 等 RBAC 参考表结构（见
 * {@code framework-security-starter/src/main/resources/db/schema-rbac.sql}），
 * 但那只是参考实现，业务方可以完全不用。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
@FunctionalInterface
public interface AuthUserLoader {

    /**
     * 按登录名加载用户。
     *
     * <p>实现方需自行处理逻辑删除过滤——框架无法假设业务方的删除语义。
     *
     * @param username 登录名
     * @return 用户；不存在时返回 {@link Optional#empty()}，<b>不要抛异常</b>
     *         （是否存在属于认证结果的一部分，由框架统一转换为对外的错误信息）
     */
    Optional<AuthUser> loadByUsername(String username);
}
