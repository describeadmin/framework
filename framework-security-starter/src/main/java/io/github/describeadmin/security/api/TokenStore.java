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
     * 带来源信息的签发。
     *
     * <p><b>为什么是 default 方法</b>：与 {@link #issueWithRefresh(LoginUser)}、
     * {@link #listActive()} 同理——在接口发布之后新增，写成抽象方法会让所有已实现
     * {@code TokenStore} 的业务方直接编译失败。默认实现忽略 {@code meta}、直接委托给
     * {@link #issue(LoginUser)}，语义是"本实现不记录登录来源"，此时
     * {@link ActiveSession#getIp()} / {@link ActiveSession#getDevice()} 为 {@code null}。
     * 框架内置的两个实现（{@code InMemoryTokenStore} / {@code RedisTokenStore}）会把
     * {@code meta} 存进会话。
     *
     * @param user 认证结果，不为 null
     * @param meta 登录来源，不为 null（不关心时传 {@link SessionMeta#EMPTY}）
     */
    default String issue(LoginUser user, SessionMeta meta) {
        return issue(user);
    }

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
     * <p><b>支持刷新令牌的实现必须同时吊销该用户名下的全部 refresh token</b>——
     * 否则"改密码/禁用立即失效"会被一个仍然有效的 refresh token 绕过：
     * access token 过期后，前端拿这个仍有效的 refresh token 换出新的 access token，
     * 等于本方法什么都没做到。这不是一个可以分批修的独立漏洞，必须与
     * {@link #issueWithRefresh(LoginUser)} 在同一次改动里落地。
     *
     * @return 实际吊销的令牌数
     */
    int revokeAllOf(Long userId);

    /**
     * 同时签发 access/refresh 令牌对。
     *
     * <p><b>为什么是 default 方法</b>：本方法是在接口发布之后新增的，写成抽象方法会让
     * 所有已实现 {@code TokenStore} 的业务方直接编译失败。默认实现只签发 access token，
     * {@link IssuedTokens#getRefreshToken()} 为 {@code null}，语义是"本实现不支持刷新令牌"——
     * 前端应据此不再调用 {@code /api/auth/refresh}。
     *
     * @param user 认证结果，不为 null
     */
    default IssuedTokens issueWithRefresh(LoginUser user) {
        return new IssuedTokens(issue(user), null);
    }

    /**
     * 带来源信息的 access/refresh 令牌对签发，语义见 {@link #issue(LoginUser, SessionMeta)}
     * 与 {@link #issueWithRefresh(LoginUser)}。默认实现忽略 {@code meta}。
     *
     * @param user 认证结果，不为 null
     * @param meta 登录来源，不为 null（不关心时传 {@link SessionMeta#EMPTY}）
     */
    default IssuedTokens issueWithRefresh(LoginUser user, SessionMeta meta) {
        return issueWithRefresh(user);
    }

    /**
     * 用 refresh token 换发新的一对令牌。
     *
     * <p>支持刷新的实现应当做<b>轮换</b>：换发成功后旧的 refresh token 立即失效，
     * 缩小泄露窗口——一个 refresh token 只能使用一次。
     *
     * <p>刷新只延长会话，<b>不会重新拉取角色/权限</b>——与"权限快照在登录时确定"
     * 是同一既有取舍（见 docs/CLAUDE.md §4.5 第 3 条）：管理员改了权限后，
     * 用户仍需重新登录或被 {@link #revokeAllOf(Long)} 才能让新权限生效，
     * 单纯刷新 access token 不会绕开这一条。
     *
     * <p>refresh token 不存在、已过期、已被使用过一次，或已被
     * {@link #revokeAllOf(Long)}/{@link #revokeRefreshToken(String)} 吊销时返回空
     * {@link Optional}——<b>不要抛异常</b>，调用方（{@code AuthController}）应将其
     * 转换为"请重新登录"的语义。
     *
     * <p>默认实现返回空，表示"本实现不支持刷新"。
     */
    default Optional<IssuedTokens> refresh(String refreshToken) {
        return Optional.empty();
    }

    /**
     * 单独吊销一个 refresh token（例如登出时一并清理）。
     *
     * <p>令牌不存在时静默返回。默认空实现。
     */
    default void revokeRefreshToken(String refreshToken) {
    }

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
