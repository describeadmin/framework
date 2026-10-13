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
 * <p>正常登录不要求验证码；下面三个维度<b>任一</b>达到阈值后，才要求请求体携带
 * {@code captchaId}/{@code captchaCode} 并校验通过：
 * <ol>
 *   <li><b>用户名</b>：同一用户名连续失败达到 {@code triggerThreshold}，复用
 *       {@link LoginAttemptGuard} 已有的失败计数，不新起一套计数</li>
 *   <li><b>IP</b> / <b>全局</b>：由 {@link LoginRiskCounter} 按固定时间窗计数。只看用户名的话，
 *       密码喷洒与撞库（每个用户名只试一两次）永远触发不了验证码</li>
 * </ol>
 *
 * <p>独立成类而不是塞进 {@code AuthController}/{@code UsernamePasswordAuthProvider}：
 * 可以独立单元测试；{@code AuthController} 保持薄；不用碰属于兼容性承诺范围之外、
 * 但被多处依赖的登录实现类。
 *
 * <p><b>已知取舍</b>：
 * <ol>
 *   <li>验证码答错/缺失时，请求在到达具体 {@code AuthProvider} 之前就被拒绝——
 *       这次尝试<b>不计入</b>任何维度的失败次数。好处是不会出现
 *       "验证码不断答错也把账号锁死"的两套计数联动放大；代价是验证码本身答错不会
 *       加速锁定，但验证码这道防线本身已经在拦截。</li>
 *   <li>{@link #attemptGuard} 为 {@code null}（对应
 *       {@code describeadmin.security.lockout.enabled=false}）时，<b>用户名维度</b>永远不触发
 *       （fail-open）——不应该因为失败次数限制被关闭，就让依赖它计数的功能用另一种方式生效。
 *       IP / 全局维度有自己的计数（{@link LoginRiskCounter}），不受锁定开关影响，照常工作。</li>
 * </ol>
 */
public class CaptchaGuard {

    private final CaptchaProvider captchaProvider;
    private final LoginAttemptGuard attemptGuard;
    private final LoginRiskCounter riskCounter;
    private final int triggerThreshold;
    private final Set<String> applicableTypes;

    /** 只有用户名维度的构造函数，保留以兼容既有调用方。 */
    public CaptchaGuard(CaptchaProvider captchaProvider, LoginAttemptGuard attemptGuard,
                         int triggerThreshold, Collection<String> applicableTypes) {
        this(captchaProvider, attemptGuard, null, triggerThreshold, applicableTypes);
    }

    /**
     * @param attemptGuard 用户名维度的计数来源，可为 {@code null}（此维度不触发）
     * @param riskCounter  IP / 全局维度的计数来源，可为 {@code null}（这两个维度不触发）
     */
    public CaptchaGuard(CaptchaProvider captchaProvider, LoginAttemptGuard attemptGuard,
                         LoginRiskCounter riskCounter, int triggerThreshold,
                         Collection<String> applicableTypes) {
        if (captchaProvider == null) {
            throw new IllegalArgumentException("CaptchaProvider 不能为空");
        }
        if (triggerThreshold <= 0) {
            throw new IllegalArgumentException("触发阈值必须为正数，当前为: " + triggerThreshold);
        }
        this.captchaProvider = captchaProvider;
        this.attemptGuard = attemptGuard;
        this.riskCounter = riskCounter;
        this.triggerThreshold = triggerThreshold;
        this.applicableTypes = new LinkedHashSet<>(
                applicableTypes == null ? Set.of() : applicableTypes);
    }

    /** 不带客户端 IP 的版本，等同于 {@code verifyIfRequired(type, body, null)}：IP 维度不参与判定。 */
    public void verifyIfRequired(String type, Map<String, Object> body) {
        verifyIfRequired(type, body, null);
    }

    /**
     * 按需校验验证码。在具体 {@code AuthProvider} 认证之前调用。
     *
     * @param type     登录方式标识
     * @param body     登录请求体，读取 {@code username}/{@code captchaId}/{@code captchaCode} 三个字段
     * @param clientIp 客户端 IP，可为空。<b>必须是不可伪造的来源</b>（直连地址，或经受信代理还原的地址），
     *                 不能直接取 {@code X-Forwarded-For}——否则每次请求换一个伪造值即可绕过 IP 维度
     * @throws BizException {@link ResultCode#CAPTCHA_REQUIRED}（缺字段）或
     *                       {@link ResultCode#CAPTCHA_INVALID}（校验不通过）
     */
    public void verifyIfRequired(String type, Map<String, Object> body, String clientIp) {
        if (!applicableTypes.contains(type) || !required(body, clientIp)) {
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

    /**
     * 登录认证失败后调用，累计 IP / 全局维度的失败次数。
     *
     * <p>用户名维度不在这里计——那由 {@code AuthProvider} 自己调 {@link LoginAttemptGuard#recordFailure}，
     * 它才知道失败是不是凭证错误。
     */
    public void recordFailure(String type, String clientIp) {
        if (riskCounter != null && applicableTypes.contains(type)) {
            riskCounter.recordFailure(clientIp);
        }
    }

    private boolean required(Map<String, Object> body, String clientIp) {
        if (riskCounter != null && riskCounter.exceedsThreshold(clientIp)) {
            return true;
        }
        if (attemptGuard == null) {
            return false;
        }
        String username = String.valueOf(body.getOrDefault("username", ""));
        // 空用户名交给对应 AuthProvider 自己拒绝，用户名维度不重复处理
        return StringUtils.hasText(username) && attemptGuard.failureCount(username) >= triggerThreshold;
    }
}
