package io.github.describeadmin.security.api;

import java.util.List;
import java.util.Optional;

/**
 * 登录令牌的签发、解析与吊销。
 *
 * <p>做成 SPI 而不是写死一种实现，是因为部署形态差异很大：单机部署用内存即可，
 * 多实例或需要"踢下线"审计的项目会换成 Redis，涉密项目可能要求令牌落库留痕。
 * 框架只依赖本接口，换实现不影响任何上层代码。
 *
 * <p>框架内置 {@code InMemoryTokenStore} 作为默认实现。业务方注册自己的
 * {@code TokenStore} Bean 即自动覆盖（见 FrameworkSecurityAutoConfiguration 的
 * {@code @ConditionalOnMissingBean}）。
 *
 * <p><b>刻意选择不透明令牌而非 JWT</b>：JWT 一旦签发就无法在过期前收回，
 * 而管理后台的"禁用用户立即失效""强制下线"是常规需求，用 JWT 需要额外维护黑名单，
 * 复杂度并不低于直接用服务端存储。此外不透明令牌不引入任何新依赖。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface TokenStore {

    /**
     * 为已认证通过的用户签发令牌。
     *
     * @param user 认证结果，不为 null
     * @return 令牌字符串，前端在 {@code Authorization: Bearer <token>} 中回传
     */
    String issue(LoginUser user);

    /**
     * 解析令牌。
     *
     * <p>令牌不存在、已过期或已被吊销时返回空 {@link Optional}，
     * <b>不要抛异常</b>——未登录是正常流量，不是异常情况。
     */
    Optional<LoginUser> resolve(String token);

    /** 吊销单个令牌（登出）。令牌不存在时静默返回。 */
    void revoke(String token);

    /**
     * 吊销某用户的全部令牌（禁用账号、改密码、强制下线）。
     *
     * @return 实际吊销的令牌数
     */
    int revokeAllOf(Long userId);

    /**
     * 列出当前全部在线会话，供"在线用户"管理页使用。
     *
     * <p><b>为什么是 default 方法</b>：本方法是在接口发布之后新增的，
     * 写成抽象方法会让所有已实现 {@code TokenStore} 的业务方直接编译失败——
     * 那是一次没有必要的破坏性变更。默认返回空列表，语义是"本实现不支持枚举"。
     *
     * <p>确实存在无法枚举的实现：譬如把令牌委托给外部统一认证中心的实现，
     * 它根本不持有会话集合。这类实现保留默认行为即可，在线用户页会显示为空，
     * 而不是抛异常把整个页面打死。
     *
     * @return 在线会话快照；不支持枚举时返回空列表，<b>不要抛异常</b>
     */
    default List<ActiveSession> listActive() {
        return List.of();
    }
}
