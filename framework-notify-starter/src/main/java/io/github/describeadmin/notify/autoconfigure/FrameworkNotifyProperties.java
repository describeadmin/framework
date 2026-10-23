package io.github.describeadmin.notify.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-notify-starter 的配置项，前缀 {@code describeadmin.notify}。
 */
@ConfigurationProperties(prefix = "describeadmin.notify")
public class FrameworkNotifyProperties {

    private final Log log = new Log();

    public Log getLog() {
        return log;
    }

    /** 框架内置日志通道的开关。关闭后行为与"没有这个通道"完全一致。 */
    public static class Log {

        /** 是否注册内置的日志通道（{@code channel = "log"}，把消息写进应用日志）。 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
