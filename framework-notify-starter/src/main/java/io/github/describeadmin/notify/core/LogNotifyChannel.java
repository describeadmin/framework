package io.github.describeadmin.notify.core;

import io.github.describeadmin.notify.api.NotifyChannel;
import io.github.describeadmin.notify.api.NotifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 零依赖的日志通道，框架默认值之一。
 *
 * <p>不发送到任何外部系统，只把消息内容记到日志——本地开发、以及尚未接入真实厂商
 * 通道的部署，用它验证"通知确实被触发"这条链路。与其他渠道（如未来的
 * {@code framework-notify-dingtalk-starter}）不冲突：各自用不同的 {@link #channel()}
 * 标识注册，{@code NotifyDispatcher} 按标识路由，不存在互相替换的问题。
 */
public class LogNotifyChannel implements NotifyChannel {

    /** 本渠道的标识。 */
    public static final String CHANNEL = "log";

    private static final Logger log = LoggerFactory.getLogger(LogNotifyChannel.class);

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void send(NotifyMessage message) {
        log.info("[通知/log] title={}, receivers={}, content={}",
                message.title(), message.receivers(), message.content());
    }
}
