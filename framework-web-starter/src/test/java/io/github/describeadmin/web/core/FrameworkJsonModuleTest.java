package io.github.describeadmin.web.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.web.autoconfigure.FrameworkWebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FrameworkJsonModule} 的序列化/反序列化约定。
 *
 * <p><b>断言纪律</b>（CLAUDE.md 3.6）：一律比对具体字符串，不只断言「是字符串类型」。
 * 雪花 ID 被舍入时类型仍然正确、长度也一样，只有逐字符比对才看得出来——
 * 这跟「中文乱码但 COUNT(*) 正常」是同一类欺骗性故障。
 */
@DisplayName("FrameworkJsonModule JSON 约定")
class FrameworkJsonModuleTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = newMapper(new FrameworkWebProperties().getJson());
    }

    /** 按 Spring Boot 的装配顺序还原：先 JavaTimeModule，后框架模块，后者覆盖前者。 */
    private static ObjectMapper newMapper(FrameworkWebProperties.Json json) {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new FrameworkJsonModule(json))
                .build();
    }

    @Nested
    @DisplayName("Long 序列化")
    class LongSerialization {

        @Test
        @DisplayName("雪花量级的 Long 逐字符保真——这正是 JS 会舍入的那几位")
        void snowflakeIdKeepsEveryDigit() throws Exception {
            long snowflake = 1234567890123456789L;
            // 前提校验：这个值确实超出 JS 的安全整数范围，否则本用例没有意义
            assertThat(snowflake).isGreaterThan(9007199254740991L);

            String json = mapper.writeValueAsString(new Box(snowflake, snowflake));

            assertThat(json).isEqualTo(
                    "{\"boxed\":\"1234567890123456789\",\"primitive\":\"1234567890123456789\"}");
        }

        @Test
        @DisplayName("原始 long 与包装 Long 一视同仁——Jackson 对两者是分别查找序列化器的")
        void primitiveAndBoxedBehaveTheSame() throws Exception {
            String json = mapper.writeValueAsString(new Box(7L, 7L));

            assertThat(json).isEqualTo("{\"boxed\":\"7\",\"primitive\":\"7\"}");
        }

        @Test
        @DisplayName("null 仍是 null，不会变成字符串形态的 null")
        void nullStaysNull() throws Exception {
            String json = mapper.writeValueAsString(new Box(null, 0L));

            assertThat(json).isEqualTo("{\"boxed\":null,\"primitive\":\"0\"}");
        }

        @Test
        @DisplayName("@JsonFormat(shape = NUMBER) 是逃生舱，标了就保持数字")
        void jsonFormatNumberOptsOut() throws Exception {
            String json = mapper.writeValueAsString(new OptedOut(42L, 42L));

            assertThat(json).isEqualTo("{\"asString\":\"42\",\"asNumber\":42}");
        }

        @Test
        @DisplayName("List<Long> 的元素同样转字符串——菜单/部门 ID 列表走的就是这条路")
        void collectionElementsAreConverted() throws Exception {
            String json = mapper.writeValueAsString(new Ids(List.of(1L, 1234567890123456789L)));

            assertThat(json).isEqualTo("{\"ids\":[\"1\",\"1234567890123456789\"]}");
        }

        @Test
        @DisplayName("Integer 不受影响——version/sort/status 这类字段仍是数字")
        void integersAreUntouched() throws Exception {
            String json = mapper.writeValueAsString(new WithInt(3));

            assertThat(json).isEqualTo("{\"value\":3}");
        }

        @Test
        @DisplayName("long-as-string=false 时完全退回数字形态")
        void switchCanBeTurnedOff() throws Exception {
            FrameworkWebProperties.Json json = new FrameworkWebProperties().getJson();
            json.setLongAsString(false);

            assertThat(newMapper(json).writeValueAsString(new Box(7L, 7L)))
                    .isEqualTo("{\"boxed\":7,\"primitive\":7}");
        }

        @Test
        @DisplayName("反序列化仍接受数字与字符串两种形态——前端回传哪种都不会 400")
        void deserializationAcceptsBothForms() throws Exception {
            assertThat(mapper.readValue("{\"boxed\":\"1234567890123456789\",\"primitive\":1}", Box.class)
                    .getBoxed()).isEqualTo(1234567890123456789L);
            assertThat(mapper.readValue("{\"boxed\":123,\"primitive\":1}", Box.class)
                    .getBoxed()).isEqualTo(123L);
        }
    }

    @Nested
    @DisplayName("PageResult 的分页元信息例外")
    class PageResultBoundary {

        @Test
        @DisplayName("records 里的 id 是字符串，total/current/size/pages 是数字")
        void metadataStaysNumericWhileIdsBecomeStrings() throws Exception {
            PageResult<Box> page = new PageResult<>(
                    List.of(new Box(1234567890123456789L, 1L)), 25L, 2L, 10L);

            String json = mapper.writeValueAsString(page);

            // el-pagination 的 :total 要求数字，转成字符串组件会报类型错
            assertThat(json).contains("\"total\":25")
                    .contains("\"current\":2")
                    .contains("\"size\":10")
                    .contains("\"pages\":3");
            assertThat(json).contains("\"boxed\":\"1234567890123456789\"");
        }
    }

    @Nested
    @DisplayName("时间格式")
    class TimeFormat {

        @Test
        @DisplayName("输出统一为空格分隔，不是 Jackson 默认的 ISO-8601")
        void serializesWithSpaceSeparator() throws Exception {
            Times times = new Times(
                    LocalDateTime.of(2026, 8, 24, 15, 30, 5),
                    LocalDate.of(2026, 8, 24),
                    LocalTime.of(15, 30, 5));

            String json = mapper.writeValueAsString(times);

            assertThat(json).isEqualTo("{\"dateTime\":\"2026-08-24 15:30:05\","
                    + "\"date\":\"2026-08-24\",\"time\":\"15:30:05\"}");
        }

        @Test
        @DisplayName("空格分隔可反序列化——codegen 的日期时间选择器发的就是这个格式")
        void acceptsSpaceSeparatedInput() throws Exception {
            Times times = mapper.readValue(
                    "{\"dateTime\":\"2026-08-24 15:30:05\",\"date\":\"2026-08-24\","
                            + "\"time\":\"15:30:05\"}", Times.class);

            assertThat(times.getDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 24, 15, 30, 5));
            assertThat(times.getDate()).isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(times.getTime()).isEqualTo(LocalTime.of(15, 30, 5));
        }

        @Test
        @DisplayName("ISO 的 T 分隔仍然接受——不打断已经在发 ISO 的调用方")
        void stillAcceptsIsoInput() throws Exception {
            Times times = mapper.readValue(
                    "{\"dateTime\":\"2026-08-24T15:30:05\",\"date\":\"2026-08-24\","
                            + "\"time\":\"15:30:05\"}", Times.class);

            assertThat(times.getDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 24, 15, 30, 5));
        }

        @Test
        @DisplayName("秒可省略，小数秒可选")
        void secondsAndFractionAreOptional() throws Exception {
            assertThat(mapper.readValue("{\"dateTime\":\"2026-08-24 15:30\"}", Times.class)
                    .getDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 24, 15, 30));
            assertThat(mapper.readValue("{\"dateTime\":\"2026-08-24T15:30:05.123\"}", Times.class)
                    .getDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 24, 15, 30, 5, 123_000_000));
        }

        @Test
        @DisplayName("往返一致：序列化出去的串能原样读回来")
        void roundTrips() throws Exception {
            LocalDateTime original = LocalDateTime.of(2026, 8, 24, 15, 30, 5);

            String json = mapper.writeValueAsString(new Times(original, null, null));

            assertThat(mapper.readValue(json, Times.class).getDateTime()).isEqualTo(original);
        }

        @Test
        @DisplayName("格式非法仍然报错，不静默给出一个错误的时间")
        void rejectsGarbage() {
            assertThatThrownBy(() -> mapper.readValue("{\"dateTime\":\"24/08/2026\"}", Times.class))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("输出格式可配置——对接外部系统时可能被要求换格式")
        void formatIsConfigurable() throws Exception {
            FrameworkWebProperties.Json json = new FrameworkWebProperties().getJson();
            json.setDateTimeFormat("yyyy/MM/dd HH:mm");

            String out = newMapper(json).writeValueAsString(
                    new Times(LocalDateTime.of(2026, 8, 24, 15, 30, 5), null, null));

            assertThat(out).contains("\"dateTime\":\"2026/08/24 15:30\"");
        }
    }

    // --- 测试用的 POJO ---------------------------------------------------

    static class Box {
        private Long boxed;
        private long primitive;

        Box() {
        }

        Box(Long boxed, long primitive) {
            this.boxed = boxed;
            this.primitive = primitive;
        }

        public Long getBoxed() {
            return boxed;
        }

        public void setBoxed(Long boxed) {
            this.boxed = boxed;
        }

        public long getPrimitive() {
            return primitive;
        }

        public void setPrimitive(long primitive) {
            this.primitive = primitive;
        }
    }

    static class OptedOut {
        private final Long asString;
        private final Long asNumber;

        OptedOut(Long asString, Long asNumber) {
            this.asString = asString;
            this.asNumber = asNumber;
        }

        public Long getAsString() {
            return asString;
        }

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        public Long getAsNumber() {
            return asNumber;
        }
    }

    static class Ids {
        private final List<Long> ids;

        Ids(List<Long> ids) {
            this.ids = ids;
        }

        public List<Long> getIds() {
            return ids;
        }
    }

    static class WithInt {
        private final Integer value;

        WithInt(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }

    static class Times {
        private LocalDateTime dateTime;
        private LocalDate date;
        private LocalTime time;

        Times() {
        }

        Times(LocalDateTime dateTime, LocalDate date, LocalTime time) {
            this.dateTime = dateTime;
            this.date = date;
            this.time = time;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public LocalTime getTime() {
            return time;
        }

        public void setTime(LocalTime time) {
            this.time = time;
        }
    }
}
