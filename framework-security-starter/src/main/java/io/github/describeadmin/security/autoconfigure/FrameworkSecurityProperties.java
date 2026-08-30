package io.github.describeadmin.security.autoconfigure;

import io.github.describeadmin.security.core.UsernamePasswordAuthProvider;
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

    /** 渐进式验证码。 */
    private final Captcha captcha = new Captcha();

    /** 密码复杂度策略。 */
    private final PasswordPolicyProperties passwordPolicy = new PasswordPolicyProperties();

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

    public Captcha getCaptcha() {
        return captcha;
    }

    public PasswordPolicyProperties getPasswordPolicy() {
        return passwordPolicy;
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

    /**
     * 渐进式验证码，前缀 {@code describeadmin.security.captcha}。
     *
     * <p>正常登录不要求验证码；同一用户名连续登录失败达到 {@link #triggerThreshold} 后，
     * 才要求携带验证码——复用 {@code LoginAttemptGuard} 已有的失败计数。
     */
    public static class Captcha {

        /**
         * 是否启用渐进式验证码策略。
         *
         * <p>关闭后无论失败多少次都不要求验证码，但 {@code GET /api/auth/captcha}
         * 端点仍然可用——"能力是否存在"与"是否强制生效"是两回事，与
         * {@code PasswordEncoder}/{@code TokenStore} 的默认 Bean 同一模式。
         */
        private boolean enabled = true;

        /**
         * 触发验证码要求的连续失败次数。
         *
         * <p>必须严格小于 {@link Lockout#getMaxFailures()}，否则验证码永远赶不上生效
         * 就已经被锁定拦下——装配时会校验这一条，配置错误直接启动失败。
         */
        private int triggerThreshold = 3;

        /** 验证码有效期，同时也是答案在缓存中的存活时间。 */
        private Duration ttl = Duration.ofMinutes(2);

        /** 验证码字符长度。 */
        private int codeLength = 4;

        /** 需要渐进式验证码保护的登录方式，默认仅内置的用户名密码登录。 */
        private List<String> applicableTypes = new ArrayList<>(List.of(UsernamePasswordAuthProvider.TYPE));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTriggerThreshold() {
            return triggerThreshold;
        }

        public void setTriggerThreshold(int triggerThreshold) {
            this.triggerThreshold = triggerThreshold;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getCodeLength() {
            return codeLength;
        }

        public void setCodeLength(int codeLength) {
            this.codeLength = codeLength;
        }

        public List<String> getApplicableTypes() {
            return applicableTypes;
        }

        public void setApplicableTypes(List<String> applicableTypes) {
            this.applicableTypes = applicableTypes == null ? new ArrayList<>() : applicableTypes;
        }
    }

    /**
     * 密码复杂度策略，前缀 {@code describeadmin.security.password-policy}。
     *
     * <p>覆盖自助改密、管理员重置密码、创建用户设初始密码三处入口，
     * 默认口径是国内政务/等保场景常见的"8 位 + 至少 3 类字符"，
     * 具体校验逻辑见 {@code DefaultPasswordPolicy}。
     */
    public static class PasswordPolicyProperties {

        /** 是否启用。关闭后不做任何复杂度校验，仅保留"非空"这一底线（各设密码方法自身校验）。 */
        private boolean enabled = true;

        /** 最短长度。 */
        private int minLength = 8;

        /**
         * 大写字母/小写字母/数字/特殊字符四类中，至少需要覆盖的类别数。
         *
         * <p>取值范围 1-4；默认 3，即"任意 3 类"，不强制必须是哪 3 类。
         */
        private int minCharacterClasses = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMinCharacterClasses() {
            return minCharacterClasses;
        }

        public void setMinCharacterClasses(int minCharacterClasses) {
            this.minCharacterClasses = minCharacterClasses;
        }
    }
}
