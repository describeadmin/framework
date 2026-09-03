package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.TokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * {@link TokenAuthenticationFilter} 的单元测试。
 *
 * <p>重点是崩溃回归：授权数据里混入一条空白权限标识（菜单管理"权限标识"留空存成空串）
 * 时，本过滤器曾在 {@code new SimpleGrantedAuthority("")} 处抛
 * {@code IllegalArgumentException}。该过滤器在每个带令牌的请求上执行，一条脏数据
 * 就会让该用户的所有已认证请求整体 500——包括 {@code /api/auth/me}。
 */
@DisplayName("令牌认证过滤器")
class TokenAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("权限/角色里混入空白项时不崩，且空白项被丢弃")
    void skipsBlankAuthoritiesInsteadOfCrashing() throws Exception {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("ADMIN");
        roles.add("");            // 脏数据
        Set<String> permissions = new LinkedHashSet<>();
        permissions.add("system:user:list");
        permissions.add("");      // 权限标识留空存成的空串
        permissions.add("   ");   // 纯空白
        LoginUser user = new LoginUser(1L, "admin", "超级管理员", "password", roles, permissions);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockFilterChain chain = new MockFilterChain();

        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(stubStore("good-token", user));

        assertThatNoException().isThrownBy(
                () -> filter.doFilter(request, new MockHttpServletResponse(), chain));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).as("令牌有效，上下文应被填充").isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "system:user:list");
        assertThat(chain.getRequest()).as("请求应正常放行到后续链路").isNotNull();
    }

    @Test
    @DisplayName("无令牌时不填充上下文，直接放行")
    void passesThroughWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        new TokenAuthenticationFilter(stubStore("unused", null))
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    /** 只解析指定令牌，其余能力用不到，留空实现即可。 */
    private static TokenStore stubStore(String knownToken, LoginUser user) {
        return new TokenStore() {
            @Override
            public String issue(LoginUser u) {
                return knownToken;
            }

            @Override
            public Optional<LoginUser> resolve(String token) {
                return knownToken.equals(token) ? Optional.ofNullable(user) : Optional.empty();
            }

            @Override
            public void revoke(String token) {
            }

            @Override
            public int revokeAllOf(Long userId) {
                return 0;
            }
        };
    }
}
