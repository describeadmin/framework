package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * {@link SecurityContextPermissionChecker} 的单元测试。
 *
 * <p>权限校验错在"放行"这一侧是安全事故，错在"拒绝"这一侧只是功能不可用。
 * 因此下面每一条"应当拒绝"的用例都比"应当放行"的用例更重要。
 */
@DisplayName("SecurityContext 权限校验")
class SecurityContextPermissionCheckerTest {

    private final SecurityContextPermissionChecker checker = new SecurityContextPermissionChecker();

    @AfterEach
    void clearContext() {
        // SecurityContextHolder 默认是 ThreadLocal，不清理会串到下一个用例
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("持有该权限点时放行")
    void grantsHeldPermission() {
        authenticateWith("system:user:list", "system:user:add");

        assertThat(checker.hasPermission("system:user:add")).isTrue();
        assertThatNoException().isThrownBy(() -> checker.require("system:user:add"));
    }

    @Test
    @DisplayName("未持有该权限点时拒绝")
    void deniesMissingPermission() {
        authenticateWith("system:user:list");

        assertThat(checker.hasPermission("system:user:remove")).isFalse();
    }

    @Test
    @DisplayName("权限点比对是精确匹配，不做前缀或通配")
    void matchesExactly() {
        authenticateWith("system:user:list");

        // 若实现改成 startsWith 之类的"宽松匹配"，下面几条会变成放行——那是越权
        assertThat(checker.hasPermission("system:user")).isFalse();
        assertThat(checker.hasPermission("system:user:list:extra")).isFalse();
        assertThat(checker.hasPermission("system")).isFalse();
    }

    @Test
    @DisplayName("角色不能当权限点用——两者在 authorities 里靠 ROLE_ 前缀区分")
    void roleIsNotAPermission() {
        authenticateWith(Set.of("ADMIN"), Set.of("system:user:list"));

        assertThat(checker.hasPermission("ADMIN")).isFalse();
        assertThat(checker.hasPermission("ROLE_ADMIN")).isTrue();
    }

    @Test
    @DisplayName("无登录上下文时一律拒绝")
    void deniesWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThat(checker.hasPermission("system:user:list")).isFalse();
    }

    @Test
    @DisplayName("匿名认证不算已认证的用户，同样拒绝")
    void deniesAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous",
                        List.of(new SimpleGrantedAuthority("system:user:list"))));

        // 匿名令牌的 isAuthenticated() 为 true，仅凭该标志判断会把匿名用户当成已授权
        assertThat(checker.hasPermission("system:user:list")).isFalse();
    }

    @Test
    @DisplayName("空权限点视为无权限，不会误放行")
    void deniesBlankPermission() {
        authenticateWith("system:user:list");

        assertThat(checker.hasPermission(null)).isFalse();
        assertThat(checker.hasPermission("")).isFalse();
        assertThat(checker.hasPermission("   ")).isFalse();
    }

    @Test
    @DisplayName("require 失败时抛 AccessDeniedException，而不是框架自己的 BizException")
    void requireThrowsAccessDenied() {
        authenticateWith("system:user:list");

        // 类型选择不是风格问题：只有 AccessDeniedException 才会被
        // SecurityExceptionHandler 映射成 403，与 @PreAuthorize 的拒绝产出同一份响应
        assertThatThrownBy(() -> checker.require("system:user:remove"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("system:user:remove");
    }

    // ------------------------------------------------------------------ 工具

    private void authenticateWith(String... permissions) {
        authenticateWith(Set.of(), Set.of(permissions));
    }

    /**
     * 按 {@link TokenAuthenticationFilter} 的真实做法构造上下文：
     * 角色加 {@code ROLE_} 前缀，权限点原样。
     */
    private void authenticateWith(Set<String> roles, Set<String> permissions) {
        LoginUser user = new LoginUser(1L, "tester", "测试用户", "password", roles, permissions);
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "token", authorities));
    }
}
