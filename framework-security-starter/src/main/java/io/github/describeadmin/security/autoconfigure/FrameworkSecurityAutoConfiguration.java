package io.github.describeadmin.security.autoconfigure;

import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.security.core.UsernamePasswordAuthProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * framework-security-starter 的自动配置。
 */
@AutoConfiguration
public class FrameworkSecurityAutoConfiguration {

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
}
