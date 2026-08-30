package io.github.describeadmin.notify.autoconfigure;

import io.github.describeadmin.notify.api.NotifyChannel;
import io.github.describeadmin.notify.core.LogNotifyChannel;
import io.github.describeadmin.notify.core.NotifyDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * framework-notify-starter 的自动配置。
 *
 * <p><b>与 {@code FrameworkCacheAutoConfiguration} 的形状不同，读这段之前先想清楚这一点</b>：
 * {@link NotifyChannel} 是多实现共存模型（见接口注释），本类注册的 {@link LogNotifyChannel}
 * 不是"占位符，插件引入后被整体替换"的默认实现，而是与任何插件渠道<b>永久并存</b>的一个
 * 普通渠道，只是用 {@code "log"} 这个标识占位。
 *
 * <p><b>因此未来的通知插件（钉钉/企业微信/短信）绝不能照抄
 * {@code framework-cache-redis-starter} 的 {@code @ConditionalOnMissingBean(CacheProvider.class)}
 * 套路，把自己的 {@code @Bean} 标成 {@code @ConditionalOnMissingBean(NotifyChannel.class)}</b>——
 * 本类注册的 {@code LogNotifyChannel} 几乎总是先于插件被评估，一旦插件也用这个条件，
 * Spring 看到的是"已经存在一个 NotifyChannel 类型的 Bean"，插件自己的渠道会被静默挡掉，
 * 启动不会有任何报错。插件渠道应当无条件注册（只受自身的 {@code @ConditionalOnProperty}
 * 开关控制），真正的"标识冲突"交给 {@link NotifyDispatcher} 的构造函数在启动期报错。
 * 同理，本类也不需要、不应该给 {@link NotifyDispatcher} 声明
 * {@code @AutoConfiguration(before = ...)}——没有谁会"挡住"谁，不存在需要排序的顺序依赖。
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkNotifyProperties.class)
public class FrameworkNotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogNotifyChannel.class)
    @ConditionalOnProperty(prefix = "describeadmin.notify.log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public LogNotifyChannel logNotifyChannel() {
        return new LogNotifyChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyDispatcher notifyDispatcher(List<NotifyChannel> channels) {
        return new NotifyDispatcher(channels);
    }
}
