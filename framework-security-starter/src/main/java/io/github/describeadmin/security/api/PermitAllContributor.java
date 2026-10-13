package io.github.describeadmin.security.api;

import java.util.Collection;

/**
 * 由其他模块贡献的免认证路径。
 *
 * <p>框架内置白名单只放登录相关的接口；其余 starter 若有"必须免登录才成立"的端点，
 * 注册一个本接口的 Bean 即可，过滤链在构建时把它们追加到白名单里。
 * 与业务方配置的 {@code describeadmin.security.permit-all} 同为<b>追加</b>关系。
 *
 * <p>存在这个扩展点，是为了让安全层不需要知道其他模块的路径。典型例子是头像读取端点：
 * {@code <img src>} 不会带 {@code Authorization} 头，读取端点只能免登录；
 * 如果改成让业务方自己配 {@code permit-all}，那么默认配置下头像就是 401。
 *
 * <p><b>贡献的每一条路径都等于对未登录访问者开放</b>。只贡献那些即使被匿名访问也不会泄露什么的端点，
 * 并且在端点内部自行约束可访问的范围（例如只允许读某个固定前缀下的对象）。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
@FunctionalInterface
public interface PermitAllContributor {

    /** Ant 风格的路径模式，例如 {@code /api/system/avatar/**}。 */
    Collection<String> patterns();
}
