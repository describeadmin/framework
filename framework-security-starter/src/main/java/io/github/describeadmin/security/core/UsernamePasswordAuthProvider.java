package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.AuthUser;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.LoginUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * 内置的用户名密码登录。
 *
 * <p>框架自带的唯一一种登录方式；浙政钉等其余方式由 {@code framework-auth-*-starter} 插件提供。
 */
public class UsernamePasswordAuthProvider implements AuthProvider {

    public static final String TYPE = "password";

    private final AuthUserLoader userLoader;
    private final PasswordEncoder passwordEncoder;

    /** 失败次数限制；为 null 表示未启用（{@code describeadmin.security.lockout.enabled=false}）。 */
    private final LoginAttemptGuard attemptGuard;

    /**
     * 用于恒定时间比对的占位哈希：用户不存在时也跑一次 {@code matches}，抹平"存在/不存在"的响应耗时差。
     *
     * <p>启动时用注入的 {@code passwordEncoder} 现算一个随机串的哈希——既与实际算法一致，
     * 又保证其明文不对应任何真实密码（不再是某个固定弱口令的哈希）。
     */
    private final String dummyHash;

    /** 不启用失败次数限制的构造函数，保留以兼容既有调用方。 */
    public UsernamePasswordAuthProvider(AuthUserLoader userLoader, PasswordEncoder passwordEncoder) {
        this(userLoader, passwordEncoder, null);
    }

    public UsernamePasswordAuthProvider(AuthUserLoader userLoader, PasswordEncoder passwordEncoder,
                                        LoginAttemptGuard attemptGuard) {
        this.userLoader = userLoader;
        this.passwordEncoder = passwordEncoder;
        this.attemptGuard = attemptGuard;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int order() {
        // 内置方式排在最前，插件通过覆写 order() 调整位置
        return -100;
    }

    @Override
    public LoginUser authenticate(AuthRequest request) {
        String username = request.getString("username");
        String password = request.getString("password");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BizException(ResultCode.AUTH_FAILED, "用户名或密码不能为空");
        }

        // 在查库与哈希比对之前拦截：被锁定期间不应再消耗数据库与 BCrypt 的开销，
        // 那本身就是一条可被利用的放大信道
        if (attemptGuard != null) {
            attemptGuard.assertNotLocked(username);
        }

        Optional<AuthUser> found = userLoader.loadByUsername(username);

        // 用户不存在时也执行一次哈希比对，抹平"存在"与"不存在"的响应耗时差异，
        // 避免通过时序差异枚举出系统中有哪些账号。
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            // 不存在的用户名同样计数，否则"会不会被锁"就成了账号枚举信道，
            // 上面那行抹平时序差异的努力也就白费了
            recordFailure(username);
            throw new BizException(ResultCode.AUTH_FAILED, "用户名或密码错误");
        }

        AuthUser user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFailure(username);
            // 对外不区分"用户不存在"与"密码错误"，两者返回同一条信息
            throw new BizException(ResultCode.AUTH_FAILED, "用户名或密码错误");
        }

        // 密码正确即清零——后面的禁用判断不是凭证错误，不该继续累计爆破计数
        if (attemptGuard != null) {
            attemptGuard.reset(username);
        }

        if (!user.isEnabled()) {
            throw new BizException(ResultCode.AUTH_FAILED, "账号已被禁用");
        }

        return user.toLoginUser(TYPE);
    }

    private void recordFailure(String username) {
        if (attemptGuard != null) {
            attemptGuard.recordFailure(username);
        }
    }
}
