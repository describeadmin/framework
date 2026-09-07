package io.github.describeadmin.cache.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式锁：标在需要防并发的接口方法或定时任务方法上。
 *
 * <p>两个典型场景：
 * <ul>
 *   <li><b>接口防重复提交</b>：锁键含业务标识（如订单号），同一业务标识的
 *       并发请求只有一个能进入</li>
 *   <li><b>定时任务防多机并发</b>：多实例部署时只有抢到锁的节点执行本轮任务，
 *       其余节点本轮直接跳过。此场景<b>必须</b>是 {@link LockStrategy#FAIL_FAST}
 *       ——等待策略会让排在后面的节点在锁释放后把任务再执行一遍，启动期校验
 *       会拒绝这种组合</li>
 * </ul>
 *
 * <p>注意 AOP 的通用限制：注解只在<b>经过代理的调用</b>上生效，同类内部
 * 自调用（this.method()）不会触发。
 *
 * <h2>与事务的顺序</h2>
 * 切面已排在事务拦截器之前：获取锁 → 开事务 → 提交 → 释放锁。
 * 若业务方自行将事务拦截器的 order 调到切面之前，"锁释放后事务才提交"的
 * 窗口会重新出现，属于业务方自担的显式覆盖。
 *
 * <h2>单机实现的天花板</h2>
 * 未引入 {@code framework-cache-redis-starter} 时生效的是进程内锁，
 * <b>多实例部署下互不感知</b>——锁只在单 JVM 内有效。多实例部署请引入
 * Redis 插件。启动日志会打印当前生效的锁实现。
 *
 * <p>本注解位于 {@code api} 包下，属于兼容性承诺范围。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 锁键，SpEL 表达式，如 {@code "'order:' + #orderNo"}。
     *
     * <p>缺省使用 {@code 全限定类名.方法名}——对定时任务这正是想要的：
     * 一个任务方法一把全局键，无需手写。
     */
    String key() default "";

    /** 锁的存活时间（秒）。持有者宕机后锁靠它自动开放，应显著大于业务的正常执行时长。 */
    int ttl() default 30;

    /** {@link LockStrategy#WAIT} 策略下的最长等待时间（秒）。 */
    int waitTimeout() default 10;

    /** 获取策略。{@link LockStrategy#DEFAULT}（缺省）跟随全局配置。 */
    LockStrategy strategy() default LockStrategy.DEFAULT;
}
