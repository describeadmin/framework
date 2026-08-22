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

    /**
     * 是否启用权限点校验。
     *
     * <p>默认开启。关闭后 {@code PermissionChecker} 不注册，退回
     * {@code PermissionChecker.PERMIT_ALL}，{@code BaseController} 的通用端点不再校验权限点；
     * {@code @PreAuthorize} 也随 {@code @EnableMethodSecurity} 一并失效。
     *
     * <p><b>只应在排查问题时临时关闭</b>。关闭状态下任何已登录用户都能调用任何接口——
     * 权限点仍会下发给前端用于按钮显隐，于是界面看起来是受控的，实际并不受控。
     */
    private boolean permissionEnabled = true;

    /** 登录失败次数限制。 */
    private final Lockout lockout = new Lockout();

    /** access/refresh 双令牌。 */
    private final RefreshToken refreshToken = new RefreshToken();

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

    public boolean isPermissionEnabled() {
        return permissionEnabled;
    }

    public void setPermissionEnabled(boolean permissionEnabled) {
        this.permissionEnabled = permissionEnabled;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    /**
     * 登录失败次数限制，前缀 {@code describeadmin.security.lockout}。
     *
     * <p>可被用于拒绝服务——知道用户名的人可以故意输错密码把对方锁住。
     * 这是按用户名锁定的固有代价，见 {@code LoginAttemptGuard} 的类注释。
     */
    public static class Lockout {

        /** 是否启用。关闭后登录失败不计数，在线爆破只受网络吞吐限制。 */
        private boolean enabled = true;

        /** 锁定窗口内允许的连续失败次数。 */
        private int maxFailures = 5;

        /**
         * 锁定时长，同时也是失败计数的存活时间。
         *
         * <p>到点自动解锁，不需要管理员介入——需要人工解锁的设计在政务场景里
         * 会直接变成一线的运维负担。
         */
        private Duration duration = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }
    }

    /**
     * access/refresh 双令牌，前缀 {@code describeadmin.security.refresh-token}。
     *
     * <p>关闭后 {@code AuthController.login()} 退回只签发 access token
     * （{@code IssuedTokens.refreshToken} 为 null），前端应据此不再调用 {@code /auth/refresh}。
     * 这是不换 {@link io.github.describeadmin.security.api.TokenStore} 实现即可关闭
     * 刷新令牌策略的开关——两层开关模型（编译期是否具备能力 vs 运行时是否启用）
     * 在核心内部同样适用。
     */
    public static class RefreshToken {

        /** 是否签发 refresh token。 */
        private boolean enabled = true;

        /**
         * refresh token 有效期。
         *
         * <p>明显长于 access token 的 {@link #tokenTtl}——它存在的意义就是让用户
         * 不必在 access token 每次过期时都重新输入密码。默认 7 天。
         */
        private Duration ttl = Duration.ofDays(7);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }
}
