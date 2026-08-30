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

    /**
     * 按用户 id 加载用户，供认证插件在把凭证换成 {@code userId} 之后拼装完整用户使用。
     *
     * <p><b>为什么是 default 方法</b>：本方法是在接口发布之后新增的，
     * 写成抽象方法会让所有已实现 {@code AuthUserLoader} 的业务方直接编译失败——
     * 那是一次没有必要的破坏性变更（手法同 {@link TokenStore#listActive()}）。
     * 默认返回空，语义是"本实现不支持按 id 反查"。
     *
     * <p>典型场景：手机号/邮箱/第三方登录等插件式 {@code AuthProvider} 先把凭证换成
     * {@code userId}（核心字段如手机号直接查 {@code SysUserService}；第三方 openId
     * 等自建映射表），再调用本方法拿到角色/权限/数据权限/首页路径俱全的
     * {@link AuthUser}，不用重新实现一遍"由用户 id 拼装完整用户"的逻辑。
     *
     * @param userId 用户 id
     * @return 用户；不存在或本实现不支持按 id 查询时返回 {@link Optional#empty()}，
     *         <b>不要抛异常</b>
     */
    default Optional<AuthUser> loadByUserId(Long userId) {
        return Optional.empty();
    }
}
