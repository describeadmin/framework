package io.github.describeadmin.web.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-web-starter 的配置项，前缀 {@code describeadmin.web}。
 */
@ConfigurationProperties(prefix = "describeadmin.web")
public class FrameworkWebProperties {

    private final Trace trace = new Trace();

    private final Json json = new Json();

    public Trace getTrace() {
        return trace;
    }

    public Json getJson() {
        return json;
    }

    public static class Trace {

        /** 是否启用链路追踪过滤器。 */
        private boolean enabled = true;

        /** 过滤器顺序相对 {@code HIGHEST_PRECEDENCE} 的偏移量。 */
        private int orderOffset = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getOrderOffset() {
            return orderOffset;
        }

        public void setOrderOffset(int orderOffset) {
            this.orderOffset = orderOffset;
        }
    }

    /**
     * JSON 序列化约定，见 {@code FrameworkJsonModule}。
     *
     * <p>格式项刻意做成可配置而不是写死：对接外部系统时可能被要求换格式，
     * 但默认值应当适配 codegen 生成的前端（日期时间选择器发的是空格分隔格式）。
     */
    public static class Json {

        /** 是否启用框架的 JSON 约定。关闭后完全退回 Spring Boot 默认行为。 */
        private boolean enabled = true;

        /**
         * 是否把 {@code Long}/{@code long} 序列化成字符串。
         *
         * <p>默认开启，避免雪花 ID 被 JS 舍入。关闭前请确认没有使用雪花 ID，
         * 且将来也不会切换——切换之后的故障是静默的（见 {@code LongToStringSerializer}）。
         */
        private boolean longAsString = true;

        /**
         * {@code LocalDateTime} 的输出格式，{@code Instant} 按服务器默认时区换算后也用它。
         * 输入不受此限制：{@code T} 与空格分隔、省略秒都能解析。
         */
        private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

        /** {@code LocalDate} 的输出格式。输入固定按 {@code yyyy-MM-dd} 解析。 */
        private String dateFormat = "yyyy-MM-dd";

        /** {@code LocalTime} 的输出格式。输入时秒与小数秒可省略。 */
        private String timeFormat = "HH:mm:ss";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLongAsString() {
            return longAsString;
        }

        public void setLongAsString(boolean longAsString) {
            this.longAsString = longAsString;
        }

        public String getDateTimeFormat() {
            return dateTimeFormat;
        }

        public void setDateTimeFormat(String dateTimeFormat) {
            this.dateTimeFormat = dateTimeFormat;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }

        public String getTimeFormat() {
            return timeFormat;
        }

        public void setTimeFormat(String timeFormat) {
            this.timeFormat = timeFormat;
        }
    }
}
