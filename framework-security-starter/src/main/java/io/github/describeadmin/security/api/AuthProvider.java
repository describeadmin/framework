package io.github.describeadmin.security.api;

/**
 * 登录方式 SPI。
 *
 * <p>框架核心只定义契约、不感知具体实现。新增一种登录方式（浙政钉、企业微信……）时，
 * 在独立的 {@code framework-auth-*-starter} 模块中实现本接口并注册为 Bean 即可，
 * 框架通过 {@code List<AuthProvider>} 自动收集。
 *
 * <p><b>约束</b>：framework-core 的任何代码里都不允许出现具体实现的标识字符串
 * （如 {@code "zhengwuding"}、{@code "dingtalk"}），那些只能出现在对应的 ext 模块内。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围——一旦被业务方实现，
 * 方法签名变更即为 Breaking Change，需走第七章的版本治理流程。
 */
public interface AuthProvider {

    /**
     * 登录方式标识，需全局唯一。
     *
     * <p>该值会通过 {@code /api/auth/providers} 暴露给前端，前端据此动态渲染登录方式，
     * 而不是把按钮硬编码在登录页里。
     */
    String type();

    /**
     * 是否支持给定的登录方式标识。
     *
     * <p>默认按 {@link #type()} 做相等比较。需要一个实现兼容多个别名时可覆写。
     */
    default boolean supports(String type) {
        return type().equals(type);
    }

    /**
     * 执行认证。
     *
     * @param request 认证请求
     * @return 认证成功的用户
     * @throws io.github.describeadmin.common.api.BizException 认证失败时抛出
     */
    LoginUser authenticate(AuthRequest request);

    /**
     * 展示顺序，值小者靠前。前端登录页按此排序渲染。
     */
    default int order() {
        return 0;
    }
}
