package io.github.describeadmin.web.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.web.core.FrameworkJsonModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 约定的装配行为。
 *
 * <p>除了"Bean 在不在"，这里还要验证一件更重要的事：框架的 Module 确实被
 * Spring Boot 自动配置的 {@link ObjectMapper} 装上了。只断言 Bean 存在是不够的——
 * Module Bean 建了但没被 ObjectMapper 收走，是一种"引了却没生效、启动毫无异常"的
 * 失败形态（与 registry.md 准入规范第 3 条描述的插件装配陷阱同类）。
 */
@DisplayName("framework-web-starter JSON 自动配置")
class FrameworkWebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, FrameworkWebAutoConfiguration.class));

    @Test
    @DisplayName("默认装配，且约定真正作用到了自动配置的 ObjectMapper 上")
    void moduleIsAppliedToAutoConfiguredObjectMapper() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FrameworkJsonModule.class);

            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(new Holder(1234567890123456789L)))
                    .isEqualTo("{\"id\":\"1234567890123456789\"}");
        });
    }

    @Test
    @DisplayName("enabled=false 时不装配，行为完全退回 Spring Boot 默认")
    void canBeDisabled() {
        runner.withPropertyValues("describeadmin.web.json.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(FrameworkJsonModule.class);

            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(new Holder(1234567890123456789L)))
                    .isEqualTo("{\"id\":1234567890123456789}");
        });
    }

    @Test
    @DisplayName("格式配置项能透传到实际输出")
    void propertiesReachTheModule() {
        runner.withPropertyValues("describeadmin.web.json.long-as-string=false").run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(new Holder(7L))).isEqualTo("{\"id\":7}");
        });
    }

    @Test
    @DisplayName("业务方定义同类型 Bean 时框架退让")
    void backsOffForUserBean() {
        runner.withUserConfiguration(CustomModuleConfig.class).run(context -> {
            assertThat(context).hasSingleBean(FrameworkJsonModule.class);
            assertThat(context.getBean(FrameworkJsonModule.class))
                    .isSameAs(context.getBean(CustomModuleConfig.class).custom);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomModuleConfig {

        private final FrameworkJsonModule custom =
                new FrameworkJsonModule(new FrameworkWebProperties().getJson());

        @Bean
        FrameworkJsonModule customJsonModule() {
            return custom;
        }
    }

    static class Holder {
        private final Long id;

        Holder(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }
}
