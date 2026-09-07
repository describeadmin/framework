package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.DistributedLock;
import io.github.describeadmin.cache.api.LockStrategy;
import io.github.describeadmin.cache.autoconfigure.FrameworkLockProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 启动期校验：拒绝"定时任务 + 等待策略"的组合。
 *
 * <p><b>为什么必须拒绝而不是放行</b>：多实例部署下，节点 A 拿锁执行任务期间，
 * WAIT 策略的节点 B 会一直等到 A 释放，<b>然后把同一轮任务再执行一遍</b>——
 * 等待策略用在定时任务上，制造出来的恰恰是要防的重复执行。正确语义只有
 * FAIL_FAST：拿不到锁 = 别的节点在跑，本轮跳过。
 *
 * <p>校验在所有单例就绪后（{@link SmartInitializingSingleton}）而不是等第一次执行：
 * 配置错误应在部署当天暴露，而不是三个月后对账时。生效策略按"注解显式值优先、
 * DEFAULT 跟随全局配置"解析，与切面运行时的算法一致——全局配置是 WAIT 时，
 * 没写 strategy 的 @Scheduled 方法同样会被拒绝。
 *
 * <p>实现上只解析 Bean <b>类型</b>（{@code getType}），不触发实例化——
 * 惰性 Bean 一样会被查到，且不会打乱容器正常的初始化顺序。
 */
public class LockStartupValidator implements SmartInitializingSingleton {

    private final FrameworkLockProperties properties;
    private final ConfigurableListableBeanFactory beanFactory;

    public LockStartupValidator(FrameworkLockProperties properties,
                                ConfigurableListableBeanFactory beanFactory) {
        this.properties = properties;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType == null) {
                continue;
            }
            // MetadataLookup 返回 null 的方法才会入选（返回 Boolean.FALSE 也会入选，
            // 这是 MethodIntrospector 的约定，这里曾真的踩过）
            Map<Method, Boolean> annotated = MethodIntrospector.selectMethods(beanType,
                    (MethodIntrospector.MetadataLookup<Boolean>) method -> {
                        boolean bothMarked = AnnotatedElementUtils.hasAnnotation(method, DistributedLock.class)
                                && AnnotatedElementUtils.hasAnnotation(method, Scheduled.class);
                        return bothMarked ? Boolean.TRUE : null;
                    });
            annotated.keySet().forEach(method -> checkMethod(beanType, method));
        }
    }

    private void checkMethod(Class<?> beanType, Method method) {
        DistributedLock lock = AnnotatedElementUtils.findMergedAnnotation(method, DistributedLock.class);
        if (lock == null || effectiveStrategy(lock) != LockStrategy.WAIT) {
            return;
        }
        throw new IllegalStateException(String.format(
                "@DistributedLock 标注在 @Scheduled 方法 %s.%s 上且生效策略为 WAIT：%n"
                        + "  等待策略会让其他节点在锁释放后把同一轮任务再执行一遍，"
                        + "这正是要防的问题。%n"
                        + "  修正方式：给注解显式指定 strategy = LockStrategy.FAIL_FAST，"
                        + "或把 describeadmin.lock.default-strategy 配置为 FAIL_FAST。",
                beanType.getSimpleName(), method.getName()));
    }

    private LockStrategy effectiveStrategy(DistributedLock lock) {
        return lock.strategy() == LockStrategy.DEFAULT
                ? properties.getDefaultStrategy()
                : lock.strategy();
    }
}
