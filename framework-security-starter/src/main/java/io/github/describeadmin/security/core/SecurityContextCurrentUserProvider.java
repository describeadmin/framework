package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.security.api.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文取当前用户 ID，供审计字段填充等场景使用。
 *
 * <p>这是 framework-security-starter 对 framework-common 中
 * {@link CurrentUserProvider} 契约的实现——引入了鉴权模块，
 * 审计字段里的创建人/更新人就自动有值，业务代码一行不用改。
 */
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        // 匿名访问时 principal 是字符串 "anonymousUser"，不是 LoginUser
        return authentication.getPrincipal() instanceof LoginUser user ? user.getUserId() : null;
    }
}
