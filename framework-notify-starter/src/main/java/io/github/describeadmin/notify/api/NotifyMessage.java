package io.github.describeadmin.notify.api;

import java.util.List;

/**
 * 一条待发送的通知消息。
 *
 * <p>刻意保持得很少，与 {@code CacheProvider}/{@code StorageProvider} 同样的取舍。
 *
 * @param title     标题；部分渠道（如短信）没有标题概念，允许为 {@code null}
 * @param content   正文，不能为空
 * @param receivers 接收方标识列表；具体含义由渠道决定（userId、手机号、openid 等），
 *                  框架核心不解释这些字符串的含义。{@code null} 归一化为空列表
 */
public record NotifyMessage(String title, String content, List<String> receivers) {

    public NotifyMessage {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("通知内容不能为空");
        }
        receivers = receivers == null ? List.of() : List.copyOf(receivers);
    }
}
