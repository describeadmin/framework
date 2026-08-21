package io.github.describeadmin.system.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * framework-system-starter 的配置项，前缀 {@code describeadmin.system}。
 */
@ConfigurationProperties(prefix = "describeadmin.system")
public class FrameworkSystemProperties {

    private final Dict dict = new Dict();
    private final Config config = new Config();
    private final OperLog operLog = new OperLog();

    public Dict getDict() {
        return dict;
    }

    public Config getConfig() {
        return config;
    }

    public OperLog getOperLog() {
        return operLog;
    }

    /** 字典，前缀 {@code describeadmin.system.dict}。 */
    public static class Dict {

        /** 按字典类型查询的结果缓存多久。字典数据读多写少，写操作后会主动失效，
         * 这里的 TTL 只是双保险，不必设得很短。 */
        private Duration cacheTtl = Duration.ofMinutes(30);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 参数配置，前缀 {@code describeadmin.system.config}。 */
    public static class Config {

        /** 按 key 查询的结果缓存多久，取舍同 {@link Dict#cacheTtl}。 */
        private Duration cacheTtl = Duration.ofMinutes(30);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 操作日志，前缀 {@code describeadmin.system.oper-log}。 */
    public static class OperLog {

        /**
         * 是否启用操作日志切面。
         *
         * <p>关闭后行为等同于没有引入这个机制——{@code BaseController} 的通用写端点
         * 与已标注 {@code @OperLog} 的自定义端点都不再记录。仅建议在排查问题
         * （如怀疑切面本身拖慢了请求）时临时关闭。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
