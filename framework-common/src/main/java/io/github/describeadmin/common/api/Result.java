package io.github.describeadmin.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 统一响应体。
 *
 * <p>Controller 一律返回本类型，不要自行拼装响应结构。异常交给全局异常处理器，
 * 不要在 Controller 里 try-catch 后返回错误码。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围，签名变更需走 SemVer。
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;

    /** 链路追踪 ID，由 web-starter 的过滤器填充；无追踪上下文时为 null。 */
    private final String traceId;

    private Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    /** 返回一个附带 traceId 的副本，用于全局异常处理器补充追踪信息。 */
    public Result<T> withTraceId(String traceId) {
        return new Result<>(this.code, this.message, this.data, traceId);
    }

    public boolean isSuccess() {
        return this.code == ResultCode.OK.getCode();
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }
}
