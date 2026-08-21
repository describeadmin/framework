package io.github.describeadmin.notify.api;

/**
 * 消息通知渠道契约。
 *
 * <p>与 {@code CacheProvider}/{@code TokenStore} 不同，本 SPI 天然是<b>多实现共存</b>模型，
 * 不是"一个默认实现，插件引入后整体替换"模型——一个部署里可以同时启用日志通道、
 * 钉钉通道、短信通道，各自按 {@link #channel()} 返回的标识区分，互不冲突。
 * 框架核心通过收集全部 {@code NotifyChannel} Bean 组成 {@code NotifyDispatcher}
 * 完成路由，而不是用 {@code @ConditionalOnMissingBean} 做"谁存在就用谁"的替换。
 *
 * <p><b>框架核心代码里不允许出现任何具体渠道实现的名字</b>（"dingtalk"、"wecom" 等字符串
 * 只能出现在对应的插件模块内）——见 CLAUDE.md 4.2。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface NotifyChannel {

    /**
     * 渠道标识，如 {@code "dingtalk"}、{@code "sms"}。框架内置的日志通道用
     * {@code "log"}（见 {@code LogNotifyChannel#CHANNEL}）。
     *
     * <p>同一个 Spring 上下文中，不允许有两个 {@code NotifyChannel} Bean 返回相同的标识——
     * {@code NotifyDispatcher} 会在启动期发现并拒绝这种情况。
     */
    String channel();

    /** 发送一条消息。具体的失败重试、限流策略由各实现自行决定，本契约不作要求。 */
    void send(NotifyMessage message);
}
