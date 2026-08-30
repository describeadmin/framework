package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 授权失败的响应映射。
 *
 * <p><b>本类为什么必须存在</b>：framework-web-starter 的 {@code GlobalExceptionHandler}
 * 有一个 {@code @ExceptionHandler(Throwable.class)} 兜底。Controller 方法里抛出的
 * {@code AccessDeniedException}（无论来自 {@code @PreAuthorize} 还是
 * {@link SecurityContextPermissionChecker#require}）会先被 DispatcherServlet 的
 * 异常解析器捕获，落到那个兜底分支，变成 <b>HTTP 500 + code 50000</b>——
 * 根本到不了过滤器链上的 {@link ResultAuthenticationEntryPoint}。
 *
 * <p>症状很有欺骗性：权限校验其实生效了（确实拦住了），但前端看到的是"服务器内部错误"，
 * 排查方向会完全跑偏。因此授权异常必须在 advice 层显式接住。
 *
 * <p>响应体与 {@link ResultAuthenticationEntryPoint} 保持逐字一致（同样是
 * {@code Result.fail(ResultCode.FORBIDDEN)}），使"过滤器链上被拒"与"进了 Controller 才被拒"
 * 对客户端不可区分。
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 是必需的：异常解析器按 advice 的顺序挑选，
 * 先命中哪个 advice 就用哪个，不会跨 advice 比较方法的具体程度。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("授权失败: {}", ex.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return Result.fail(ResultCode.UNAUTHORIZED);
    }
}
