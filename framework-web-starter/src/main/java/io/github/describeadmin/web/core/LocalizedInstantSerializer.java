package io.github.describeadmin.web.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 把 {@link Instant} 序列化成与 {@code LocalDateTime} 同款的 {@code yyyy-MM-dd HH:mm:ss}
 * 字符串，按服务器默认时区（{@link ZoneId#systemDefault()}）转换。
 *
 * <p><b>为什么需要这个类</b>：{@link Instant} 不携带时区，Jackson 内置的
 * {@code JavaTimeModule} 对它的默认序列化是 UTC 的 ISO-8601（形如
 * {@code 2026-08-24T07:30:05.123456789Z}），既不是 {@link FrameworkJsonModule}
 * 统一的 {@code yyyy-MM-dd HH:mm:ss} 格式，也不是本框架"前端按浏览器本地"的时区口径——
 * 未经处理直接暴露给前端，表现为与其余时间列"格式不一致、还早/晚了几个小时"。
 *
 * <p>本框架的既定时区口径是 DB {@code DATETIME} → Java {@code LocalDateTime} →
 * 前端按浏览器本地（见 CLAUDE.md 4.7），链路上刻意不出现 {@link Instant}。
 * {@link io.github.describeadmin.security.api.ActiveSession} 是一个有意为之的例外——
 * 在线会话的过期判定需要一个真正的绝对时间点，{@code LocalDateTime} 做不到这件事。
 * 这个序列化器让例外只停留在 Java 侧：输出到前端时仍换算成服务器本地时间点的同款字符串，
 * 不需要前端额外识别一种新格式。
 *
 * <p>只做序列化，不提供反序列化：目前没有任何 {@code @RequestBody} 需要把
 * {@link Instant} 当作输入接收（在线会话是只读快照），真出现这个需求时再补，
 * 避免为不存在的调用方猜测一套"出严进宽"的解析规则。
 */
public class LocalizedInstantSerializer extends JsonSerializer<Instant> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DateTimeFormatter formatter;

    public LocalizedInstantSerializer(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(formatter.withZone(ZoneId.systemDefault()).format(value));
    }
}
