package io.github.describeadmin.security.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.security.api.LoginUser;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link PasswordResetRequiredFilter} 的单元测试。
 *
 * <p>门禁错在"放行"是安全事故，错在"拦截"只是功能不可用——"应拦截"的用例最重要。
 */
@DisplayName("强制改密门禁")
class PasswordResetRequiredFilterTest {

    private final PasswordResetRequiredFilter filter = new PasswordResetRequiredFilter(new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("被标记用户访问业务接口 → 403 + PASSWORD_RESET_REQUIRED，不放行")
    void blocksFlaggedUser() throws Exception {
        authenticate(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("40105");
    }

    @Test
    @DisplayName("被标记用户改密 / me / 登出 → 放行")
    void allowsWhitelistForFlaggedUser() throws Exception {
        assertPassesThrough("PUT", "/api/auth/password", true);
        assertPassesThrough("GET", "/api/auth/me", true);
        assertPassesThrough("POST", "/api/auth/logout", true);
    }

    @Test
    @DisplayName("未标记用户不受影响")
    void ignoresUnflaggedUser() throws Exception {
        assertPassesThrough("GET", "/api/system/user", false);
    }

    @Test
    @DisplayName("未登录请求直接放行，交给后续授权规则")
    void ignoresAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private void assertPassesThrough(String method, String uri, boolean pwdResetRequired) throws Exception {
        authenticate(pwdResetRequired);
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static void authenticate(boolean pwdResetRequired) {
        LoginUser user = new LoginUser(1L, "alice", "小爱", "password", Set.of(), Set.of(),
                null, null, Set.of(), null, pwdResetRequired);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "token", java.util.List.of()));
    }
}
