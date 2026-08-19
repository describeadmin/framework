package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证请求。
 *
 * <p>不同登录方式需要的入参差异很大（用户名密码 / 授权码 / 票据……），因此除了
 * {@code type} 之外一律放在 {@code params} 里，避免每加一种登录方式就要改这个类的签名
 * ——那会让本类成为新的耦合点，正是插件化要避免的。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class AuthRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String type;
    private final Map<String, Object> params;

    public AuthRequest(String type, Map<String, Object> params) {
        this.type = type;
        this.params = params == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getString(String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
