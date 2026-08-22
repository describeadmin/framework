package io.github.describeadmin.security.api;

import java.io.Serializable;

/**
 * 一次签发产出的令牌对。
 *
 * <p>{@code refreshToken} 可以为 {@code null}——语义是"本次签发不含刷新令牌"，
 * 可能是因为 {@link TokenStore} 的实现本身不支持刷新（见
 * {@link TokenStore#issueWithRefresh(LoginUser)} 的默认实现），也可能是业务方通过
 * {@code describeadmin.security.refresh-token.enabled=false} 主动关闭了这一能力。
 * 调用方（前端）拿到 {@code refreshToken == null} 时不应该再尝试调用
 * {@code /api/auth/refresh}，应该在 access token 过期后直接引导用户重新登录。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public final class IssuedTokens implements Serializable {

    private final String accessToken;
    private final String refreshToken;

    public IssuedTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
