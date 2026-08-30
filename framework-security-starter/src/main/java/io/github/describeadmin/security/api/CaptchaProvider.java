package io.github.describeadmin.security.api;

/**
 * 验证码 SPI。
 *
 * <p>核心内置图形字符验证码的默认实现（{@code ImageCaptchaProvider}）。未来的滑块验证码、
 * Cloudflare Turnstile、阿里云/腾讯云验证码等由独立的 {@code framework-captcha-<vendor>-starter}
 * 插件提供，注册自己的 {@link CaptchaProvider} Bean 即可覆盖默认实现——本接口不需要为它们改动，
 * 不同验证方式所需的渲染数据形状差异很大，因此放在 {@link CaptchaChallenge#getPayload()} 里，
 * 而不是加在本接口签名上，与 {@link AuthRequest} 解决"不同登录方式入参形状不同"是同一手法。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface CaptchaProvider {

    /** 生成一次新的验证码挑战。 */
    CaptchaChallenge generate();

    /**
     * 校验答案。
     *
     * <p><b>一次性</b>：无论校验结果对错，同一个 {@code captchaId} 校验一次后即失效，
     * 不能重复使用——否则攻击者可以对同一张验证码反复重放去撞密码。
     *
     * @param captchaId {@link CaptchaChallenge#getCaptchaId()}
     * @param answer    用户提交的答案；图形验证码语义下是用户输入的字符，大小写不敏感
     * @return 校验是否通过；{@code captchaId} 不存在或已过期同样返回 {@code false}，
     *         不抛异常——调用方统一转换成业务错误码
     */
    boolean verify(String captchaId, String answer);
}
