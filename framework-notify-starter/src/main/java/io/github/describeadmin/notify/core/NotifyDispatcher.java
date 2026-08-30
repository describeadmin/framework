package io.github.describeadmin.notify.core;

import io.github.describeadmin.notify.api.NotifyChannel;
import io.github.describeadmin.notify.api.NotifyMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按 {@link NotifyChannel#channel()} 键路由的通知调度器。
 *
 * <p>收集 Spring 上下文中全部 {@link NotifyChannel} Bean。这是 {@code CacheProvider}/
 * {@code TokenStore} 那套"单一默认实现 + {@code @ConditionalOnMissingBean} 整体替换"
 * 模式在本 SPI 上的对应物——因为通知渠道天然多个共存，替换的单位不是"整个 SPI"，
 * 而是"某一个渠道标识"，所以由本类在构造期收敛、去重、路由，而不是让某个渠道的
 * 自动配置类去挡住另一个。
 *
 * <p><b>两处都选择"启动期/调用期立即失败"而不是"静默兜底"</b>：
 * <ul>
 *   <li>构造期发现重复的 {@code channel()} 键——两个渠道抢同一个标识是接线错误，
 *       同一进程里谁生效是不确定的，必须在启动期就暴露，不能拖到某次调用撞上了才发现</li>
 *   <li>{@link #send} 收到未注册的渠道标识——通知没有"未命中回源"这回事，
 *       调用方以为消息发出去了，实际上因为拼错渠道名或忘了引对应插件而根本没有发送，
 *       没有任何旁路能让接收方发现这一点。静默吞掉等于把接线错误伪装成发送成功，
 *       所以直接抛异常，让问题在联调阶段就暴露，而不是等到"为什么没收到通知"的报障</li>
 * </ul>
 */
public class NotifyDispatcher {

    private final Map<String, NotifyChannel> channels;

    public NotifyDispatcher(List<NotifyChannel> channels) {
        Map<String, NotifyChannel> map = new LinkedHashMap<>();
        for (NotifyChannel channel : channels) {
            String key = channel.channel();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("NotifyChannel.channel() 不能为空: " + channel.getClass());
            }
            NotifyChannel existing = map.putIfAbsent(key, channel);
            if (existing != null) {
                throw new IllegalStateException(
                        "重复的通知渠道标识 \"" + key + "\": " + existing.getClass() + " 与 " + channel.getClass());
            }
        }
        this.channels = Map.copyOf(map);
    }

    /** 发送到指定渠道。渠道不存在时抛异常，见类注释。 */
    public void send(String channel, NotifyMessage message) {
        NotifyChannel target = channels.get(channel);
        if (target == null) {
            throw new IllegalArgumentException("未注册的通知渠道: \"" + channel + "\"，已注册: " + channels.keySet());
        }
        target.send(message);
    }

    /** 广播到全部已注册渠道。没有任何渠道注册时静默返回。 */
    public void broadcast(NotifyMessage message) {
        for (NotifyChannel target : channels.values()) {
            target.send(message);
        }
    }

    /** 已注册的渠道标识集合，供排查与测试使用。 */
    public Set<String> channels() {
        return channels.keySet();
    }
}
