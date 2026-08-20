package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.PermissionChecker;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 读取权限点。
 *
 * <p>{@link TokenAuthenticationFilter} 已把每个权限点作为 {@code SimpleGrantedAuthority}
 * 放进 authorities（角色另加 {@code ROLE_} 前缀），因此这里只需做一次集合比对，
 * 不需要回源查库——权限快照在登录时确定，与 {@code /api/auth/me} 下发给前端的是同一份。
 *
 * <p>这也意味着<b>权限变更不会对已登录会话实时生效</b>，需重新登录或吊销令牌。
 * 这是不透明令牌 + 登录期快照这一设计的既有取舍，不是本类的缺陷。
 */
public class SecurityContextPermissionChecker implements PermissionChecker {

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        // 匿名令牌的 isAuthenticated() 同样返回 true，只看那个标志会把匿名用户当成已授权。
        // 当前的过滤器链不会给匿名令牌挂业务权限点，但这条防线不能依赖"上游恰好没这么做"。
        if (authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (permission.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 覆写默认实现，改抛 {@code AccessDeniedException}。
     *
     * <p>这样 {@code BaseController} 通用端点的拒绝，与业务方自己写的
     * {@code @PreAuthorize} 的拒绝，走的是同一条异常类型，最终由
     * {@link SecurityExceptionHandler} 产出同一份响应体。
     */
    @Override
    public void require(String permission) {
        if (!hasPermission(permission)) {
            throw new AccessDeniedException("缺少权限: " + permission);
        }
    }
}
