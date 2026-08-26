package io.github.describeadmin.common.api;

/**
 * 统一响应码。
 *
 * <p>取值区间约定：
 * <ul>
 *   <li>{@code 0}          —— 成功</li>
 *   <li>{@code 400xx}      —— 客户端错误（参数、校验）</li>
 *   <li>{@code 401xx/403xx} —— 认证与授权</li>
 *   <li>{@code 404xx}      —— 资源不存在</li>
 *   <li>{@code 500xx}      —— 服务端错误</li>
 *   <li>{@code 9xxxx}      —— 业务方自定义区间，框架不占用</li>
 * </ul>
 *
 * <p>本枚举位于 {@code api} 包下，属于兼容性承诺范围。新增枚举值是兼容变更，
 * 修改或删除已有值是 Breaking Change。
 */
public enum ResultCode {

    OK(0, "成功"),

    BAD_REQUEST(40000, "请求参数错误"),
    VALIDATION_FAILED(40001, "参数校验失败"),

    UNAUTHORIZED(40100, "未认证或登录已过期"),
    AUTH_PROVIDER_NOT_FOUND(40101, "不支持的登录方式"),
    AUTH_FAILED(40102, "认证失败"),
    CAPTCHA_REQUIRED(40103, "需要验证码"),
    CAPTCHA_INVALID(40104, "验证码错误或已过期"),

    FORBIDDEN(40300, "无权访问"),

    NOT_FOUND(40400, "资源不存在"),

    INTERNAL_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
