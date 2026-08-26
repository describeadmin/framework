package io.github.describeadmin.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.common.api.DataScopeProvider;
import io.github.describeadmin.common.api.PermissionChecker;
import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.CaptchaProvider;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.security.core.CaptchaGuard;
import io.github.describeadmin.security.core.ImageCaptchaProvider;
import io.github.describeadmin.security.core.InMemoryTokenStore;
import io.github.describeadmin.security.core.LoginAttemptGuard;
import io.github.describeadmin.security.core.ResultAuthenticationEntryPoint;
import io.github.describeadmin.security.core.SecurityContextCurrentUserProvider;
import io.github.describeadmin.security.core.SecurityContextDataScopeProvider;
import io.github.describeadmin.security.core.SecurityContextPermissionChecker;
import io.github.describeadmin.security.core.SecurityExceptionHandler;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
            "/api/auth/refresh",
            "/api/auth/providers",
            "/api/auth/captcha",
            "/actuator/health",
            "/error");

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 登录失败次数限制。
     *
     * <p>{@code describeadmin.security.lockout.enabled=false} 时不注册，
     * {@link UsernamePasswordAuthProvider} 拿到 null 后跳过全部计数逻辑。
     *
     * <p>缓存后端取自 {@link CacheProvider}：默认是内存实现（重启清零、多实例各算各的），
     * 引入集中式实现后自动升级为全局计数，本类不用改。
     */
    @Bean
    @ConditionalOnMissingBean(LoginAttemptGuard.class)
    @ConditionalOnProperty(prefix = "describeadmin.security.lockout", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public LoginAttemptGuard loginAttemptGuard(CacheProvider cacheProvider,
                                               FrameworkSecurityProperties properties) {
        FrameworkSecurityProperties.Lockout lockout = properties.getLockout();
        return new LoginAttemptGuard(cacheProvider, lockout.getMaxFailures(), lockout.getDuration());
    }

    /**
     * 验证码生成/校验能力，核心默认实现是图形字符验证码。
     *
     * <p>不受 {@code describeadmin.security.captcha.enabled} 影响——"能力是否存在"
     * 与"是否强制生效"分离，与 {@link #passwordEncoder()}/{@link #tokenStore} 是同一模式。
     * 未来的滑块验证码、Cloudflare Turnstile 等插件通过注册自己的 {@link CaptchaProvider}
     * Bean 覆盖本默认实现即可，不需要改这里。
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaProvider.class)
    public CaptchaProvider captchaProvider(CacheProvider cacheProvider, FrameworkSecurityProperties properties) {
        FrameworkSecurityProperties.Captcha captcha = properties.getCaptcha();
        return new ImageCaptchaProvider(cacheProvider, captcha.getTtl(), captcha.getCodeLength());
    }

    /**
     * 渐进式验证码的强制生效开关，受 {@code describeadmin.security.captcha.enabled} 控制。
     *
     * <p>依赖可选的 {@link LoginAttemptGuard}——关闭失败次数限制
     * （{@code describeadmin.security.lockout.enabled=false}）时验证码同样不再拦截，
     * 见 {@link CaptchaGuard} 类注释"已知取舍"第 2 条。
     *
     * <p>装配时校验 {@code triggerThreshold < lockout.maxFailures}：配置错误必须在启动期
     * 直接拒绝，而不是运行到一半才发现验证码从未生效过。
     */
    @Bean
    @ConditionalOnMissingBean(CaptchaGuard.class)
    @ConditionalOnProperty(prefix = "describeadmin.security.captcha", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CaptchaGuard captchaGuard(CaptchaProvider captchaProvider, FrameworkSecurityProperties properties,
                                     ObjectProvider<LoginAttemptGuard> attemptGuardProvider) {
        FrameworkSecurityProperties.Captcha captcha = properties.getCaptcha();
        LoginAttemptGuard attemptGuard = attemptGuardProvider.getIfAvailable();
        if (attemptGuard != null && captcha.getTriggerThreshold() >= properties.getLockout().getMaxFailures()) {
            throw new IllegalStateException(
                    "describeadmin.security.captcha.trigger-threshold(" + captcha.getTriggerThreshold()
                            + ") 必须小于 describeadmin.security.lockout.max-failures("
                            + properties.getLockout().getMaxFailures() + ")");
        }
        return new CaptchaGuard(captchaProvider, attemptGuard, captcha.getTriggerThreshold(),
                captcha.getApplicableTypes());
    }

    /**
     * 内置用户名密码登录。
     *
     * <p>仅在业务方提供了 {@link AuthUserLoader} 实现时才注册——框架不知道用户存在哪里，
     * 没有 loader 就没法认证，此时静默不注册比启动失败更合适
     * （纯插件登录的项目可能完全不需要用户名密码方式）。
     *
     * <p>{@code ObjectProvider} 承接可选的 {@link LoginAttemptGuard}：
     * 直接注入会在关闭失败次数限制时因找不到 Bean 而启动失败。
     */
    @Bean
    @ConditionalOnBean(AuthUserLoader.class)
    @ConditionalOnMissingBean(UsernamePasswordAuthProvider.class)
    public UsernamePasswordAuthProvider usernamePasswordAuthProvider(
            AuthUserLoader userLoader, PasswordEncoder passwordEncoder,
            ObjectProvider<LoginAttemptGuard> attemptGuard) {
        return new UsernamePasswordAuthProvider(userLoader, passwordEncoder,
                attemptGuard.getIfAvailable());
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
        return new InMemoryTokenStore(properties.getTokenTtl(), properties.getRefreshToken().getTtl());
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
     * 把 SecurityContext 里登录用户的部门/数据范围暴露给 framework-common 的
     * {@link DataScopeProvider} 契约，使 framework-mybatis-starter 的数据权限拦截器
     * 能在不依赖 Spring Security 的前提下做行级过滤。
     */
    @Bean
    @ConditionalOnMissingBean(DataScopeProvider.class)
    public DataScopeProvider securityContextDataScopeProvider() {
        return new SecurityContextDataScopeProvider();
    }

    /**
     * 把 SecurityContext 里的权限点暴露给 framework-common 的
     * {@link PermissionChecker} 契约，使 {@code BaseController} 的通用 CRUD 端点
     * 能在不依赖 Spring Security 的前提下做权限校验。
     *
     * <p>{@code describeadmin.security.permission-enabled=false} 时本 Bean 不注册，
     * 消费方退回 {@link PermissionChecker#PERMIT_ALL}。
     */
    @Bean
    @ConditionalOnMissingBean(PermissionChecker.class)
    @ConditionalOnProperty(prefix = "describeadmin.security", name = "permission-enabled",
            havingValue = "true", matchIfMissing = true)
    public PermissionChecker securityContextPermissionChecker() {
        return new SecurityContextPermissionChecker();
    }

    /**
     * 方法级安全，使业务方可以在自己的端点上用 {@code @PreAuthorize("hasAuthority('xxx:yyy:add')")}。
     *
     * <p>与 {@link PermissionChecker} 受同一个开关控制，避免出现"通用端点校验、
     * 自定义端点不校验"这种一半生效的状态。
     */
    @AutoConfiguration
    @EnableMethodSecurity
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "describeadmin.security", name = "permission-enabled",
            havingValue = "true", matchIfMissing = true)
    public static class MethodSecurityConfiguration {
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

        /**
         * 授权异常的 advice 映射。
         *
         * <p>无条件注册（不随 permission-enabled 开关走）：即使框架的权限点校验被关掉，
         * Spring Security 自身仍可能抛出 {@code AccessDeniedException}，
         * 而 {@code GlobalExceptionHandler} 的 Throwable 兜底会把它变成 500。
         * 见 {@link SecurityExceptionHandler} 的类注释。
         */
        @Bean
        @ConditionalOnMissingBean
        public SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
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
