package io.github.describeadmin.web.core;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import io.github.describeadmin.web.autoconfigure.FrameworkWebProperties;

import java.io.Serial;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 框架的 JSON 序列化约定。
 *
 * <p><b>为什么用 Module Bean 而不是自定义 ObjectMapper Bean</b>：
 * 自己声明 {@code @Bean ObjectMapper} 会顶掉 Spring Boot 的全部默认配置，
 * 并让业务方的 {@code spring.jackson.*} 配置项全部失效——那是个隐蔽且难查的坑。
 * Boot 会自动收集容器里所有 {@code Module} 类型的 Bean 并注册进自动配置的 ObjectMapper，
 * 且注册顺序在内置的 {@code JavaTimeModule} 之后，同类型的序列化器天然覆盖前者。
 *
 * <p>本模块管三件事：
 * <ol>
 *   <li>{@code Long}/{@code long} → 字符串，理由见 {@link LongToStringSerializer}</li>
 *   <li>时间类型的输出格式，统一为 {@code yyyy-MM-dd HH:mm:ss} 这一族，
 *       而不是 Jackson 默认的 ISO-8601（带 {@code T} 分隔符）——{@link Instant} 同样纳入，
 *       换算到服务器默认时区后复用同一套格式，理由见 {@link LocalizedInstantSerializer}</li>
 *   <li>时间类型的输入解析，<b>出严进宽</b>：同时接受空格与 {@code T} 分隔</li>
 * </ol>
 *
 * <p><b>第 3 条修的是一个真实存在的 bug</b>：codegen 生成的日期时间选择器发的是
 * {@code value-format="YYYY-MM-DD HH:mm:ss"}（空格分隔），而 Spring Boot 默认按
 * ISO-8601 反序列化 {@code LocalDateTime}，空格格式解析失败。
 * codegen 在查询参数那条路上手工绕过了（{@code value.replace(' ', 'T')}），
 * 但 {@code @RequestBody} 那条路没有——只要业务表有 {@code datetime} 字段，
 * 新增/编辑表单提交就是坏的。
 *
 * <p>实测（关掉本模块复现）该故障返回的是 <b>500 而不是 400</b>：
 * {@code GlobalExceptionHandler} 没有单独处理 {@code HttpMessageNotReadableException}，
 * 请求体解析失败会落到 {@code Throwable} 兜底分支。也就是说"日期格式填错"这种
 * 纯输入问题会报成服务器内部错误，把排查方向带偏——这一点独立于本模块，
 * 已登记在 VERSION_BASELINE.md。
 *
 * <p>反序列化保留对 ISO 的兼容，是为了不打断已经在按 ISO 发数据的调用方。
 */
public class FrameworkJsonModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 1L;

    public FrameworkJsonModule(FrameworkWebProperties.Json json) {
        super("describeadmin-json");

        if (json.isLongAsString()) {
            // 包装类型与原始类型都要注册：Jackson 对 Long.class 与 Long.TYPE 是分别查找的，
            // 只注册包装类型的话，业务方写 `private long id` 就会漏掉，且毫无提示
            addSerializer(Long.class, LongToStringSerializer.INSTANCE);
            addSerializer(Long.TYPE, LongToStringSerializer.INSTANCE);
        }

        DateTimeFormatter dateTimeOut = DateTimeFormatter.ofPattern(json.getDateTimeFormat());
        DateTimeFormatter dateOut = DateTimeFormatter.ofPattern(json.getDateFormat());
        DateTimeFormatter timeOut = DateTimeFormatter.ofPattern(json.getTimeFormat());

        addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeOut));
        addSerializer(LocalDate.class, new LocalDateSerializer(dateOut));
        addSerializer(LocalTime.class, new LocalTimeSerializer(timeOut));
        // Instant 复用 dateTimeFormat：本身不带时区，按服务器默认时区换算成同款字符串，
        // 详见 LocalizedInstantSerializer 类注释。
        addSerializer(Instant.class, new LocalizedInstantSerializer(dateTimeOut));

        addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(lenientDateTime()));
        addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE));
        addDeserializer(LocalTime.class, new LocalTimeDeserializer(lenientTime()));
    }

    /**
     * 宽松的日期时间解析器：{@code T} 与空格分隔二选一，秒与小数秒都可省略。
     *
     * <p>覆盖的实际输入形态：{@code 2026-08-24 15:30:00}（前端日期选择器）、
     * {@code 2026-08-24T15:30:00}（ISO，axios 序列化 Date 或其他系统对接）、
     * {@code 2026-08-24 15:30}（用户手输省略秒）、{@code 2026-08-24T15:30:00.123}。
     */
    private static DateTimeFormatter lenientDateTime() {
        return new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .optionalStart().appendLiteral('T').optionalEnd()
                .optionalStart().appendLiteral(' ').optionalEnd()
                .appendPattern("HH:mm")
                .optionalStart().appendPattern(":ss").optionalEnd()
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                .toFormatter();
    }

    /** 同上，秒可省略：{@code 15:30} 与 {@code 15:30:00} 都能解析。 */
    private static DateTimeFormatter lenientTime() {
        return new DateTimeFormatterBuilder()
                .appendPattern("HH:mm")
                .optionalStart().appendPattern(":ss").optionalEnd()
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                .toFormatter();
    }
}
