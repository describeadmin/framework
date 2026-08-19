package io.github.describeadmin.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.security.core.InMemoryTokenStore;
import io.github.describeadmin.security.core.ResultAuthenticationEntryPoint;
import io.github.describeadmin.security.core.SecurityContextCurrentUserProvider;
import io.github.describeadmin.security.core.TokenAuthenticationFilter;
import io.github.describeadmin.security.core.UsernamePasswordAuthProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * framework-security-starter 的自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkSecurityProperties.class)
public class FrameworkSecurityAutoConfiguration {

    /**
     * 框架内置的免认证白名单。
     *
     * <p>与业务方配置的 {@code describeadmin.security.permit-all} 是<b>追加</b>关系。
     * 登录相关接口必须在此，否则没人能登录进来。
     */
    private static final List<String> BUILT_IN_PERMIT_ALL = List.of(
            "/api/auth/login",
            "/api/auth/providers",
            "/actuator/health",
            "/error");

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 内置用户名密码登录。
     *
     * <p>仅在业务方提供了 {@link AuthUserLoader} 实现时才注册——框架不知道用户存在哪里，
     * 没有 loader 就没法认证，此时静默不注册比启动失败更合适
     * （纯插件登录的项目可能完全不需要用户名密码方式）。
     */
    @Bean
    @ConditionalOnBean(AuthUserLoader.class)
    @ConditionalOnMissingBean(UsernamePasswordAuthProvider.class)
    public UsernamePasswordAuthProvider usernamePasswordAuthProvider(
            AuthUserLoader userLoader, PasswordEncoder passwordEncoder) {
        return new UsernamePasswordAuthProvider(userLoader, passwordEncoder);
    }

    /**
     * 收集 classpath 上所有 {@link AuthProvider} 实现。
     *
     * <p>{@code ObjectProvider} 语义下没有任何实现时会得到空列表，
     * 此处直接注入 {@code List} 并允许为空，由 registry 自身在调度时报错。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthProviderRegistry authProviderRegistry(List<AuthProvider> providers) {
        return new AuthProviderRegistry(providers);
    }

    /**
     * 默认令牌存储：内存。
     *
     * <p>单机部署适用，重启即全部失效。多实例或要求重启不掉线时，
     * 业务方注册自己的 {@link TokenStore} Bean 即可覆盖，上层代码不用动。
     */
    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore tokenStore(FrameworkSecurityProperties properties) {
        return new InMemoryTokenStore(properties.getTokenTtl());
    }

    /**
     * 把 SecurityContext 里的登录用户暴露给 framework-common 的
     * {@link CurrentUserProvider} 契约，使审计字段的创建人/更新人自动有值。
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider securityContextCurrentUserProvider() {
        return new SecurityContextCurrentUserProvider();
    }

    /**
     * Web 环境下的过滤器链。
     *
     * <p>拆成内部类是为了让 {@code @EnableWebSecurity} 与 Servlet 相关的类
     * 只在 Web 应用中被加载——非 Web 的业务方（定时任务、消息消费者）
     * 同样可能引入本 starter 只为复用 {@code PasswordEncoder} 与 SPI。
     */
    @AutoConfiguration
    @EnableWebSecurity
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "describeadmin.security", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public static class WebSecurityConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ResultAuthenticationEntryPoint resultAuthenticationEntryPoint(
                ObjectProvider<ObjectMapper> objectMapper) {
            return new ResultAuthenticationEntryPoint(
                    objectMapper.getIfAvailable(ObjectMapper::new));
        }

        @Bean
        @ConditionalOnMissingBean
        public TokenAuthenticationFilter tokenAuthenticationFilter(TokenStore tokenStore) {
            return new TokenAuthenticationFilter(tokenStore);
        }

        @Bean
        @ConditionalOnMissingBean
        public SecurityFilterChain describeadminSecurityFilterChain(
                HttpSecurity http,
                FrameworkSecurityProperties properties,
                TokenAuthenticationFilter tokenFilter,
                ResultAuthenticationEntryPoint entryPoint) throws Exception {

            List<String> permitAll = new ArrayList<>(BUILT_IN_PERMIT_ALL);
            permitAll.addAll(properties.getPermitAll());

            http
                    // 令牌放在 Authorization 头里、不用 Cookie，CSRF 攻击面不存在
                    .csrf(csrf -> csrf.disable())
                    // 无状态：不建会话，登录态完全由 TokenStore 承担
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // 表单登录与 HTTP Basic 都关掉，避免 401 被换成登录页跳转
                    .formLogin(form -> form.disable())
                    .httpBasic(basic -> basic.disable())
                    .logout(logout -> logout.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(permitAll.toArray(String[]::new)).permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler(entryPoint))
                    .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);

            if (!properties.getAllowedOrigins().isEmpty()) {
                http.cors(Customizer.withDefaults());
            }
            return http.build();
        }

        /**
         * 仅在显式配置了 allowed-origins 时才注册。
         *
         * <p>不提供「默认放开」的选项：开发期图省事放开的 CORS 被原样带到生产是常见事故，
         * 前后端分离开发期请用前端 dev server 的代理（frontend 的 vite.config 已配好）。
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "describeadmin.security", name = "allowed-origins")
        public CorsConfigurationSource corsConfigurationSource(
                FrameworkSecurityProperties properties) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(properties.getAllowedOrigins());
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return source;
        }
    }
}
