package io.github.describeadmin.web.autoconfigure;

import io.github.describeadmin.web.core.FrameworkJsonModule;
import io.github.describeadmin.web.core.GlobalExceptionHandler;
import io.github.describeadmin.web.core.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * framework-web-starter 的自动配置。
 *
 * <p>全部 Bean 都带 {@link ConditionalOnMissingBean}，业务方可以自行定义同类型 Bean 覆盖框架默认实现。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FrameworkWebProperties.class)
public class FrameworkWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler frameworkGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "describeadmin.web.trace", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public TraceIdFilter frameworkTraceIdFilter(FrameworkWebProperties properties) {
        // 排在最前，保证后续所有过滤器与业务逻辑的日志都能带上 traceId
        return new TraceIdFilter(Ordered.HIGHEST_PRECEDENCE + properties.getTrace().getOrderOffset());
    }

    /**
     * 框架的 JSON 序列化约定。
     *
     * <p>注册为 {@code Module} Bean 而不是自定义 {@code ObjectMapper}——后者会顶掉
     * Boot 的全部默认配置并让 {@code spring.jackson.*} 失效。理由详见
     * {@link FrameworkJsonModule} 的类注释。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "describeadmin.web.json", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FrameworkJsonModule frameworkJsonModule(FrameworkWebProperties properties) {
        return new FrameworkJsonModule(properties.getJson());
    }
}
