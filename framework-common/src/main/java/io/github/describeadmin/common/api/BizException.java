package io.github.describeadmin.common.api;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>业务代码抛出本异常即可，由 framework-web-starter 的全局异常处理器统一转换为
 * {@link Result}。不要在 Controller 里 try-catch 后自行拼装错误响应。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        this(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    public BizException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public BizException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message, null);
    }

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
