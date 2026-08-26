package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次验证码挑战。
 *
 * <p>{@code payload} 的形状随 {@code type} 变化，前端按 {@code type} 分支渲染：
 * <ul>
 *   <li>{@code "image"}（核心默认实现）：{@code {"image": "data:image/png;base64,...."}}</li>
 *   <li>未来的滑块/第三方插件实现自行约定 key，前端相应扩展分支即可，本类不需要改</li>
 * </ul>
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class CaptchaChallenge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String captchaId;
    private final String type;
    private final Map<String, Object> payload;

    public CaptchaChallenge(String captchaId, String type, Map<String, Object> payload) {
        this.captchaId = captchaId;
        this.type = type;
        this.payload = payload == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
