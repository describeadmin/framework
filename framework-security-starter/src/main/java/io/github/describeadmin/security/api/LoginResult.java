package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功后返回给前端的内容：令牌 + 用户主体。
 *
 * <p>刻意不把令牌塞进 {@link LoginUser}：{@code LoginUser} 是认证结果，
 * 会在请求上下文里流转、也会被 {@link AuthProvider} 实现构造，
 * 而令牌是 {@link TokenStore} 签发的、只在登录这一次响应里出现。
 * 混在一起会让每个自定义 AuthProvider 都被迫关心令牌，而它们根本管不着这件事。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class LoginResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String token;

    /** 刷新令牌，可为 null——语义与 {@link IssuedTokens#getRefreshToken()} 一致。 */
    private final String refreshToken;

    /** 令牌有效期（秒），供前端决定何时提示续期。语义不变：始终是 access token 的 TTL。 */
    private final long expiresIn;

    /** 刷新令牌有效期（秒）。{@code refreshToken} 为 null 时本字段为 0。 */
    private final long refreshExpiresIn;

    private final LoginUser user;

    /**
     * 向后兼容的构造函数：不含刷新令牌（{@code refreshToken=null}、{@code refreshExpiresIn=0}）。
     */
    public LoginResult(String token, long expiresIn, LoginUser user) {
        this(token, null, expiresIn, 0, user);
    }

    public LoginResult(String token, String refreshToken, long expiresIn, long refreshExpiresIn, LoginUser user) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public LoginUser getUser() {
        return user;
    }
}
