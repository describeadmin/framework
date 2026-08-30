package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.CaptchaProvider;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 渐进式验证码触发编排。
 *
 * <p>正常登录不要求验证码；同一用户名连续登录失败达到 {@code triggerThreshold} 后，
 * 才要求请求体携带 {@code captchaId}/{@code captchaCode} 并校验通过。复用
 * {@link LoginAttemptGuard} 已有的失败计数，不新起一套计数。
 *
 * <p>独立成类而不是塞进 {@code AuthController}/{@code UsernamePasswordAuthProvider}：
 * 可以独立单元测试；{@code AuthController} 保持薄；不用碰属于兼容性承诺范围之外、
 * 但被多处依赖的登录实现类。
 *
 * <p><b>已知取舍</b>：
 * <ol>
 *   <li>验证码答错/缺失时，请求在到达具体 {@code AuthProvider} 之前就被拒绝——
 *       这次尝试<b>不计入</b> {@link LoginAttemptGuard} 的失败次数。好处是不会出现
 *       "验证码不断答错也把账号锁死"的两套计数联动放大；代价是验证码本身答错不会
 *       加速锁定，但验证码这道防线本身已经在拦截。</li>
 *   <li>{@link #attemptGuard} 为 {@code null}（对应
 *       {@code describeadmin.security.lockout.enabled=false}）时，本类永远不拦截
 *       （fail-open）——不应该因为失败次数限制被关闭，就让验证码这个依赖它的功能
 *       意外生效或意外失效，行为必须是"没有这个功能"，而不是"用另一种方式生效"。</li>
 * </ol>
 */
public class CaptchaGuard {

    private final CaptchaProvider captchaProvider;
    private final LoginAttemptGuard attemptGuard;
    private final int triggerThreshold;
    private final Set<String> applicableTypes;

    public CaptchaGuard(CaptchaProvider captchaProvider, LoginAttemptGuard attemptGuard,
                         int triggerThreshold, Collection<String> applicableTypes) {
        if (captchaProvider == null) {
            throw new IllegalArgumentException("CaptchaProvider 不能为空");
        }
        if (triggerThreshold <= 0) {
            throw new IllegalArgumentException("触发阈值必须为正数，当前为: " + triggerThreshold);
        }
        this.captchaProvider = captchaProvider;
        this.attemptGuard = attemptGuard;
        this.triggerThreshold = triggerThreshold;
        this.applicableTypes = new LinkedHashSet<>(
                applicableTypes == null ? Set.of() : applicableTypes);
    }

    /**
     * 按需校验验证码。在具体 {@code AuthProvider} 认证之前调用。
     *
     * @param type 登录方式标识
     * @param body 登录请求体，读取 {@code username}/{@code captchaId}/{@code captchaCode} 三个字段
     * @throws BizException {@link ResultCode#CAPTCHA_REQUIRED}（缺字段）或
     *                       {@link ResultCode#CAPTCHA_INVALID}（校验不通过）
     */
    public void verifyIfRequired(String type, Map<String, Object> body) {
        if (attemptGuard == null || !applicableTypes.contains(type)) {
            return;
        }
        String username = String.valueOf(body.getOrDefault("username", ""));
        if (!StringUtils.hasText(username)) {
            // 空用户名交给对应 AuthProvider 自己拒绝，这里不重复处理
            return;
        }
        if (attemptGuard.failureCount(username) < triggerThreshold) {
            return;
        }
        Object captchaId = body.get("captchaId");
        Object answer = body.get("captchaCode");
        if (captchaId == null || answer == null) {
            throw new BizException(ResultCode.CAPTCHA_REQUIRED);
        }
        if (!captchaProvider.verify(String.valueOf(captchaId), String.valueOf(answer))) {
            throw new BizException(ResultCode.CAPTCHA_INVALID);
        }
        // 验证码通过：不在这里做任何计数操作，密码对错仍由对应 AuthProvider
        // 走既有 recordFailure/reset 逻辑决定，两套计数不交叉。
    }
}
