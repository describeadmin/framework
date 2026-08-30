package io.github.describeadmin.web.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.Serial;

/**
 * 把 {@code Long} / {@code long} 序列化成 JSON 字符串。
 *
 * <p><b>为什么必须这么做</b>：JavaScript 的 {@code Number} 是 IEEE 754 双精度，
 * 安全整数上限是 {@code Number.MAX_SAFE_INTEGER}（2^53-1，16 位）。雪花 ID 是 19 位，
 * 前端 {@code JSON.parse} 会把末几位静默舍入——列表能正常显示，点编辑/删除却报
 * "记录不存在"或者改错行。CLAUDE.md 3.3 明确支持把 {@code id-type} 切成雪花，
 * 框架既然给了这个开关，就得保证切过去之后整条链路是对的。
 *
 * <p><b>逃生舱</b>：在字段或 getter 上标 {@code @JsonFormat(shape = JsonFormat.Shape.NUMBER)}
 * 即可让某个 {@code Long} 保持数字形态。框架自己在
 * {@link io.github.describeadmin.common.api.PageResult} 的四个分页元信息字段上用了它。
 *
 * <p>刻意不做的：{@code BigDecimal} 不转字符串。金额按 2 位小数计，double 能精确表示到
 * 2^53 分（约 90 万亿元），远超实际业务范围；转成字符串反而会让前端的数字输入框与
 * 排序计算都变麻烦。确有超高精度需求的业务方自行在字段上加
 * {@code @JsonSerialize(using = ToStringSerializer.class)}。
 */
public class LongToStringSerializer extends StdSerializer<Number> implements ContextualSerializer {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final LongToStringSerializer INSTANCE = new LongToStringSerializer();

    public LongToStringSerializer() {
        super(Number.class);
    }

    @Override
    public void serialize(Number value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }

    /**
     * 尊重字段上的 {@code @JsonFormat(shape = NUMBER)}，命中时退回 Jackson 默认的数字序列化。
     *
     * <p>必须实现 {@link ContextualSerializer} 才能读到属性级注解——
     * {@code ToStringSerializer} 没有实现它，所以直接注册它的话
     * {@code @JsonFormat} 会被完全忽略，排除机制形同虚设。
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) {
        JsonFormat.Value format = findFormatOverrides(provider, property, handledType());
        if (format != null && format.getShape() == JsonFormat.Shape.NUMBER) {
            return NumberSerializer.instance;
        }
        return this;
    }
}
