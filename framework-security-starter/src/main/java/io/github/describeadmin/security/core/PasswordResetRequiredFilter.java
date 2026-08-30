package io.github.describeadmin.security.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 强制改密门禁：{@link LoginUser#isPwdResetRequired()} 为 true 的会话，
 * 除"改密 / 当前用户 / 登出"外的任何请求一律返回
 * {@link ResultCode#PASSWORD_RESET_REQUIRED}（HTTP 403）。
 *
 * <p>标记的两个来源见 {@link io.github.describeadmin.security.api.AuthUser}：管理员建号 /
 * 重置密码写进 {@code sys_user.pwd_reset_required}，或密码超过 {@code sys.password.max-age-days}
 * 设定的有效期（登录时算出，不落库）。用户走 {@code PUT /api/auth/password} 改密成功后，
 * 库里的标记清零、{@code pwd_update_time} 刷新，下次登录不再被拦。
 *
 * <p>排在 {@link TokenAuthenticationFilter} 之后——它依赖 SecurityContext 里已经填好的
 * {@link LoginUser} 主体。未登录、或主体不是 {@link LoginUser} 的请求直接放行，
 * 由后续的授权规则去决定 401/403。
 */
public class PasswordResetRequiredFilter extends OncePerRequestFilter {

    /**
     * 被标记用户仍可访问的白名单：改密（解除标记的唯一途径）、查当前用户（前端渲染改密页要用）、
     * 登出。{@code /error} 一并放行，避免转发到错误页时被本过滤器二次拦截。
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "/api/auth/password", Set.of(HttpMethod.PUT.name()),
            "/api/auth/me", Set.of(HttpMethod.GET.name()),
            "/api/auth/logout", Set.of(HttpMethod.POST.name()),
            "/error", Set.of(HttpMethod.GET.name(), HttpMethod.POST.name()));

    private final ObjectMapper objectMapper;

    public PasswordResetRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof LoginUser user
                && user.isPwdResetRequired()
                && !isAllowed(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            // 显式 UTF-8：默认编码在中文 Windows 上是 GBK，消息里的中文会变成乱码
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    Result.fail(ResultCode.PASSWORD_RESET_REQUIRED));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAllowed(HttpServletRequest request) {
        Set<String> methods = ALLOWED.get(request.getRequestURI());
        return methods != null && methods.contains(request.getMethod());
    }
}
