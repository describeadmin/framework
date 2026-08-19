package io.github.describeadmin.web.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>业务代码只需抛出 {@link BizException}，由此处统一转换为 {@link Result}。
 * 不要在 Controller 里 try-catch 后自行拼装错误响应。
 *
 * <p>注意 HTTP 状态码的处理：业务错误一律返回 HTTP 200，错误信息体现在响应体的
 * {@code code} 字段里；只有认证/授权失败与服务端异常才映射到对应的 HTTP 状态码。
 * 这样前端只需统一解析响应体，不必同时处理两套错误语义。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，记 warn，不打堆栈。 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.<Void>fail(ex.getCode(), ex.getMessage()).withTraceId(currentTraceId());
    }

    /** {@code @RequestBody} 上的 Bean Validation 失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + defaultMessage(fe))
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.<Void>fail(ResultCode.VALIDATION_FAILED, detail).withTraceId(currentTraceId());
    }

    /** 方法参数/路径变量上的约束校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.<Void>fail(ResultCode.VALIDATION_FAILED, detail).withTraceId(currentTraceId());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoHandlerFoundException ex) {
        return Result.<Void>fail(ResultCode.NOT_FOUND, ex.getRequestURL()).withTraceId(currentTraceId());
    }

    /**
     * 兜底处理。
     *
     * <p>不把原始异常信息返回给客户端——异常文本可能泄露内部结构（表名、类名、SQL 片段）。
     * 客户端拿到的是 traceId，凭它到日志里定位。
     */
    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleThrowable(Throwable ex) {
        String traceId = currentTraceId();
        log.error("未处理异常, traceId={}", traceId, ex);
        return Result.<Void>fail(ResultCode.INTERNAL_ERROR).withTraceId(traceId);
    }

    private static String defaultMessage(FieldError fe) {
        return fe.getDefaultMessage() == null ? "校验失败" : fe.getDefaultMessage();
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID_KEY);
    }
}
