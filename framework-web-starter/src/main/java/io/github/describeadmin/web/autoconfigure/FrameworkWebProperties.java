package io.github.describeadmin.web.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-web-starter 的配置项，前缀 {@code describeadmin.web}。
 */
@ConfigurationProperties(prefix = "describeadmin.web")
public class FrameworkWebProperties {

    private final Trace trace = new Trace();

    public Trace getTrace() {
        return trace;
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
}
