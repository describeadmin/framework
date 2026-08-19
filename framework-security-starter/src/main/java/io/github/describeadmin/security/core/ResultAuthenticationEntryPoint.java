package io.github.describeadmin.security.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证/授权失败时返回 {@link Result} JSON，而不是 302 跳转到登录页。
 *
 * <p>Spring Security 的默认行为是重定向到表单登录页。对前后端分离的项目，
 * 这会让前端拿到一个 200 的 HTML 页面而不是 401，
 * 错误被静默吞掉、表现为"接口返回了一堆看不懂的东西"——排查成本极高。
 *
 * <p>HTTP 状态码与响应体里的 code 都要给对：前端拦截器按 HTTP 状态码决定是否跳登录页，
 * 而业务层按 {@code code} 字段判断具体原因。
 */
public class ResultAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ResultAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, int status, ResultCode code) throws IOException {
        response.setStatus(status);
        // 显式指定 UTF-8：默认编码在中文 Windows 上是 GBK，消息里的中文会变成乱码
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(code));
    }
}
