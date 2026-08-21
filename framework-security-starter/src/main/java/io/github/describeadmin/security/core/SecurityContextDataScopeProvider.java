package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.DataScopeContext;
import io.github.describeadmin.common.api.DataScopeProvider;
import io.github.describeadmin.security.api.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从 Spring Security 上下文取当前用户的数据权限上下文，供数据权限拦截器使用。
 *
 * <p>这是 framework-security-starter 对 framework-common 中 {@link DataScopeProvider}
 * 契约的实现——引入了鉴权模块，数据权限拦截器就自动有依据可过滤，业务代码一行不用改。
 *
 * <p>取的是 {@link LoginUser} 登录时刻的快照（部门、合并后的范围、自定义部门列表都是
 * 登录时算好写进去的），与角色的菜单权限是同一个取舍——数据范围改了要重新登录才生效。
 */
public class SecurityContextDataScopeProvider implements DataScopeProvider {

    @Override
    public Optional<DataScopeContext> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        // 匿名访问时 principal 是字符串 "anonymousUser"，不是 LoginUser
        if (!(authentication.getPrincipal() instanceof LoginUser user)) {
            return Optional.empty();
        }
        return Optional.of(new DataScopeContext(
                user.getUserId(), user.getDeptId(), user.getDataScope(), user.getCustomDeptIds()));
    }
}
