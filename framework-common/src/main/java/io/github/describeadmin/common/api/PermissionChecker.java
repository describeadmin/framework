package io.github.describeadmin.common.api;

/**
 * 权限点校验的来源。
 *
 * <p>与 {@link CurrentUserProvider} 同一个用意：让 <b>需要校验权限的模块</b>
 * （{@code BaseController} 的通用 CRUD 端点、数据权限、操作日志）与
 * <b>知道当前用户有哪些权限的模块</b>（framework-security-starter）彼此不直接依赖。
 * 只用 ORM 不用鉴权的业务方不应被迫把 Spring Security 拖进依赖树。
 *
 * <p>引入 framework-security-starter 时，由其自动注册一个读取 SecurityContext 的实现；
 * 未引入时保持 {@link #PERMIT_ALL}——此时整个应用本来就没有认证，
 * 再做权限校验没有意义，也没有数据可依据。
 *
 * <p><b>权限点命名</b>：{@code <模块>:<对象>:<动作>}，动作取
 * {@code list} / {@code add} / {@code edit} / {@code remove}，
 * 与 {@code seed-rbac.sql} 的种子数据、codegen 生成的 {@code menu-*.sql} 保持一致。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
@FunctionalInterface
public interface PermissionChecker {

    /** 无鉴权上下文时的默认实现，放行一切。 */
    PermissionChecker PERMIT_ALL = permission -> true;

    /**
     * @param permission 权限点，如 {@code system:user:add}
     * @return 当前用户是否持有该权限点
     */
    boolean hasPermission(String permission);

    /**
     * 校验并在无权限时抛异常。
     *
     * <p>默认抛 {@link BizException}，因为 framework-common 不依赖 Spring Security，
     * 拿不到 {@code AccessDeniedException}。framework-security-starter 的实现会覆写本方法，
     * 改抛 {@code AccessDeniedException}，使通用端点的拒绝路径与 {@code @PreAuthorize}
     * 的拒绝路径产出完全相同的响应——否则同一个"无权限"会有两种响应格式。
     *
     * @param permission 权限点
     */
    default void require(String permission) {
        if (!hasPermission(permission)) {
            throw new BizException(ResultCode.FORBIDDEN, "缺少权限: " + permission);
        }
    }
}
