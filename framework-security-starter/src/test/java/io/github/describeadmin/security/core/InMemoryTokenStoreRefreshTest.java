package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.IssuedTokens;
import io.github.describeadmin.security.api.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryTokenStore} 的 access/refresh 双令牌行为（docs/LOGIN_MODULE_AUDIT.md E 项）。
 *
 * <p>签发/解析/吊销这些既有行为已由 {@code InMemoryTokenStoreTest} 覆盖，本类只补新增的
 * {@code issueWithRefresh}/{@code refresh}/{@code revokeRefreshToken}。
 */
@DisplayName("内存令牌存储：刷新令牌")
class InMemoryTokenStoreRefreshTest {

    private final InMemoryTokenStore store = new InMemoryTokenStore(Duration.ofMinutes(30), Duration.ofDays(7));

    private static LoginUser user(long id, String username, String nickname) {
        return new LoginUser(id, username, nickname, "password", Set.of("ADMIN"), Set.of());
    }

    @Test
    @DisplayName("签发的一对令牌都能独立解析")
    void issuedPairBothResolve() {
        IssuedTokens tokens = store.issueWithRefresh(user(1L, "admin", "超级管理员"));

        assertThat(tokens.getAccessToken()).isNotBlank();
        assertThat(tokens.getRefreshToken()).isNotBlank();
        assertThat(store.resolve(tokens.getAccessToken())).isPresent();
    }

    @Test
    @DisplayName("刷新成功后旧 refresh token 立即失效（轮换）")
    void refreshRotatesOldRefreshToken() {
        IssuedTokens first = store.issueWithRefresh(user(1L, "admin", "超级管理员"));

        Optional<IssuedTokens> second = store.refresh(first.getRefreshToken());

        assertThat(second).isPresent();
        assertThat(second.get().getRefreshToken()).isNotEqualTo(first.getRefreshToken());
        // 旧的 refresh token 已被用掉，不能再用一次
        assertThat(store.refresh(first.getRefreshToken())).isEmpty();
    }

    @Test
    @DisplayName("刷新后新 access token 能正确解析出原用户")
    void refreshedAccessTokenResolvesToSameUser() {
        IssuedTokens first = store.issueWithRefresh(user(1L, "admin", "超级管理员"));

        IssuedTokens second = store.refresh(first.getRefreshToken()).orElseThrow();

        assertThat(store.resolve(second.getAccessToken()))
                .isPresent()
                .get()
                .extracting(LoginUser::getNickname)
                .isEqualTo("超级管理员");
    }

    @Test
    @DisplayName("不存在/已使用过的 refresh token 返回空")
    void refreshWithUnknownTokenReturnsEmpty() {
        assertThat(store.refresh("not-a-real-token")).isEmpty();
        assertThat(store.refresh(null)).isEmpty();
        assertThat(store.refresh("")).isEmpty();
    }

    @Test
    @DisplayName("已过期的 refresh token 返回空")
    void refreshWithExpiredTokenReturnsEmpty() throws Exception {
        InMemoryTokenStore shortLived = new InMemoryTokenStore(Duration.ofMinutes(30), Duration.ofMillis(40));
        IssuedTokens tokens = shortLived.issueWithRefresh(user(1L, "admin", "超级管理员"));

        Thread.sleep(80);

        assertThat(shortLived.refresh(tokens.getRefreshToken())).isEmpty();
    }

    @Test
    @DisplayName("revokeAllOf 必须同时吊销 access 与 refresh 令牌")
    void revokeAllOfRevokesBothAccessAndRefresh() {
        IssuedTokens tokens = store.issueWithRefresh(user(1L, "admin", "超级管理员"));

        store.revokeAllOf(1L);

        assertThat(store.resolve(tokens.getAccessToken())).isEmpty();
        // 这一条是本次改动最关键的断言：如果 refresh token 没有被一并吊销，
        // "改密码/禁用立即失效"就会被它绕过——参见 TokenStore.revokeAllOf 的 javadoc。
        assertThat(store.refresh(tokens.getRefreshToken())).isEmpty();
    }

    @Test
    @DisplayName("revokeRefreshToken 只吊销 refresh token，不影响同一用户的 access token")
    void revokeRefreshTokenOnlyAffectsRefreshToken() {
        IssuedTokens tokens = store.issueWithRefresh(user(1L, "admin", "超级管理员"));

        store.revokeRefreshToken(tokens.getRefreshToken());

        assertThat(store.resolve(tokens.getAccessToken())).isPresent();
        assertThat(store.refresh(tokens.getRefreshToken())).isEmpty();
    }

    @Test
    @DisplayName("单参数构造函数向后兼容：不支持刷新时 issueWithRefresh 的 refreshToken 为 null")
    void defaultIssueWithRefreshWithoutOverrideYieldsNullRefreshToken() {
        // TokenStore 接口默认实现的验证：一个只实现了 issue()/resolve() 的极简 TokenStore
        // 应当继承 default 方法得到 refreshToken=null 的语义，而不是编译失败。
        var minimal = new io.github.describeadmin.security.api.TokenStore() {
            private final java.util.Map<String, LoginUser> tokens = new java.util.HashMap<>();

            @Override
            public String issue(LoginUser user) {
                String token = "t-" + user.getUserId();
                tokens.put(token, user);
                return token;
            }

            @Override
            public Optional<LoginUser> resolve(String token) {
                return Optional.ofNullable(tokens.get(token));
            }

            @Override
            public void revoke(String token) {
                tokens.remove(token);
            }

            @Override
            public int revokeAllOf(Long userId) {
                return 0;
            }
        };

        IssuedTokens result = minimal.issueWithRefresh(user(1L, "admin", "超级管理员"));

        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNull();
        assertThat(minimal.refresh("anything")).isEmpty();
    }
}
