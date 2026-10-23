package io.github.describeadmin.web.core;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 上传文件超过 {@code spring.servlet.multipart.max-file-size} / {@code max-request-size}。
     *
     * <p>这个异常在请求进入 Controller 之前就抛出了，端点自己的大小校验根本执行不到；
     * 不在这里处理的话，它会落到下面的兜底分支，用户看到的是一个 500「服务器内部错误」。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过大小限制: {}", ex.getMessage());
        return Result.<Void>fail(ResultCode.BAD_REQUEST, "上传文件超过大小限制").withTraceId(currentTraceId());
    }

    /**
     * 请求体读不出来：JSON 语法错、字段类型对不上、日期格式错。
     *
     * <p>这是调用方的输入问题。不在这里处理的话它会落到兜底分支报 500，
     * 「日期填错了」被说成服务器内部错误，排查方向一开始就走偏。
     * 只回带出错的字段路径，不回带 Jackson 的原始信息——那里面有类全名。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        String field = ex.getCause() instanceof JsonMappingException jme ? fieldPath(jme) : "";
        String message = field.isEmpty() ? "请求体格式错误" : "请求体格式错误：字段 " + field;
        return Result.<Void>fail(ResultCode.BAD_REQUEST, message).withTraceId(currentTraceId());
    }

    /** 路径变量或查询参数的类型对不上，如 {@code /api/system/user/abc}。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {}={}", ex.getName(), ex.getValue());
        return Result.<Void>fail(ResultCode.BAD_REQUEST, "参数格式错误：" + ex.getName()).withTraceId(currentTraceId());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("缺少请求参数: {}", ex.getParameterName());
        return Result.<Void>fail(ResultCode.BAD_REQUEST, "缺少参数：" + ex.getParameterName())
                .withTraceId(currentTraceId());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoHandlerFoundException ex) {
        return Result.<Void>fail(ResultCode.NOT_FOUND, ex.getRequestURL()).withTraceId(currentTraceId());
    }

    /**
     * 路径没有任何映射。
     *
     * <p>Spring 6.1 起，没有匹配到 Controller 的请求会落到静态资源处理器，抛的是这个异常而不是
     * {@link NoHandlerFoundException}。不单独处理的话，写错一个接口路径得到的是 500，
     * 日志里只有「No static resource」，看不出是路径写错了。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException ex) {
        return Result.<Void>fail(ResultCode.NOT_FOUND, "/" + ex.getResourcePath()).withTraceId(currentTraceId());
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

    /** 拼成 {@code items[0].date} 这种形式；只用字段名与下标，不带类名。 */
    private static String fieldPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String defaultMessage(FieldError fe) {
        return fe.getDefaultMessage() == null ? "校验失败" : fe.getDefaultMessage();
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.TRACE_ID_KEY);
    }
}
