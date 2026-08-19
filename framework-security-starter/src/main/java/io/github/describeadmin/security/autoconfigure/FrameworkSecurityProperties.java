package io.github.describeadmin.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * framework-security-starter 的配置项，前缀 {@code describeadmin.security}。
 */
@ConfigurationProperties(prefix = "describeadmin.security")
public class FrameworkSecurityProperties {

    /**
     * 是否由框架接管 Spring Security 的过滤器链。
     *
     * <p>置为 {@code false} 时框架不注册 {@code SecurityFilterChain}，
     * 由业务方自行定义——有特殊鉴权需求（如接入统一身份平台的网关）时用得上。
     * 注意关闭后 Spring Security 会退回默认配置（全部接口需要表单登录），
     * 业务方必须自己补上过滤器链，否则接口全部不可用。
     */
    private boolean enabled = true;

    /**
     * 令牌有效期。
     *
     * <p>默认 8 小时，覆盖一个工作日。政务场景常要求更短，通过本项调整。
     */
    private Duration tokenTtl = Duration.ofHours(8);

    /**
     * 无需认证即可访问的路径（Ant 风格）。
     *
     * <p>业务方配置的值会<b>追加</b>到框架内置白名单之后，而不是替换——
     * 替换语义下业务方漏写 {@code /api/auth/login} 就会把自己锁在门外，
     * 而这个错误要到部署后才暴露。
     */
    private List<String> permitAll = new ArrayList<>();

    /**
     * 允许跨域的来源。
     *
     * <p>留空（默认）表示<b>不启用 CORS</b>。前后端分离开发期推荐用前端 dev server 的代理
     * （frontend 的 vite.config 已配好），从根本上不产生跨域，而不是在后端放开来源——
     * 开发期放开的 CORS 配置被原样带上生产是常见事故。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public List<String> getPermitAll() {
        return permitAll;
    }

    public void setPermitAll(List<String> permitAll) {
        this.permitAll = permitAll == null ? new ArrayList<>() : permitAll;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
    }
}
