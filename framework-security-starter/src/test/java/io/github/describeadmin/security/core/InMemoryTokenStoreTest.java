package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.ActiveSession;
import io.github.describeadmin.security.api.IssuedTokens;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.SessionMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryTokenStore} 在线会话枚举的单元测试。
 *
 * <p>签发/解析/吊销的行为已由 framework-it 的 {@code AuthFlowIT} 端到端覆盖，
 * 这里只补新增的 {@code listActive}。
 */
@DisplayName("内存令牌存储：在线会话")
class InMemoryTokenStoreTest {

    private final InMemoryTokenStore store = new InMemoryTokenStore(Duration.ofMinutes(30));

    private static LoginUser user(long id, String username, String nickname) {
        return new LoginUser(id, username, nickname, "password", Set.of("ADMIN"), Set.of());
    }

    @Test
    @DisplayName("签发后出现在在线列表里，中文昵称不丢失")
    void issuedTokenAppears() {
        store.issue(user(1L, "admin", "超级管理员"));

        List<ActiveSession> sessions = store.listActive();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getUsername()).isEqualTo("admin");
        assertThat(sessions.get(0).getNickname()).isEqualTo("超级管理员");
        assertThat(sessions.get(0).getAuthType()).isEqualTo("password");
        assertThat(sessions.get(0).getIssuedAt()).isNotNull();
        assertThat(sessions.get(0).getExpiresAt()).isAfter(sessions.get(0).getIssuedAt());
    }

    @Test
    @DisplayName("同一用户多设备登录产生多条会话")
    void multipleSessionsPerUser() {
        store.issue(user(1L, "admin", "超级管理员"));
        store.issue(user(1L, "admin", "超级管理员"));

        // 会话粒度而非用户粒度：管理页面需要看到"这个人开了几个端"
        assertThat(store.listActive()).hasSize(2);
    }

    @Test
    @DisplayName("登出后从在线列表中消失")
    void revokedTokenDisappears() {
        String token = store.issue(user(1L, "admin", "超级管理员"));
        store.issue(user(2L, "bob", "小明"));

        store.revoke(token);

        assertThat(store.listActive())
                .extracting(ActiveSession::getUsername)
                .containsExactly("bob");
    }

    @Test
    @DisplayName("强制下线后该用户的全部会话消失，其他人不受影响")
    void revokeAllOfRemovesEverySessionOfThatUser() {
        store.issue(user(1L, "admin", "超级管理员"));
        store.issue(user(1L, "admin", "超级管理员"));
        store.issue(user(2L, "bob", "小明"));

        assertThat(store.revokeAllOf(1L)).isEqualTo(2);
        assertThat(store.listActive())
                .extracting(ActiveSession::getUsername)
                .containsExactly("bob");
    }

    @Test
    @DisplayName("已过期但尚未清理的令牌不出现在在线列表里")
    void expiredSessionsAreNotListed() throws Exception {
        InMemoryTokenStore shortLived = new InMemoryTokenStore(Duration.ofMillis(40));
        shortLived.issue(user(1L, "admin", "超级管理员"));

        Thread.sleep(80);

        // 惰性清理意味着条目可能还在 map 里，但它已不能用于认证，
        // 列进"在线用户"会让管理员看到并不存在的会话
        assertThat(shortLived.listActive()).isEmpty();
    }

    @Test
    @DisplayName("登录来源 IP 与设备写进会话，出现在在线列表里")
    void sessionMetaIsSurfaced() {
        store.issue(user(1L, "admin", "超级管理员"),
                new SessionMeta("203.0.113.7", "Chrome · Windows"));

        ActiveSession s = store.listActive().get(0);
        assertThat(s.getIp()).isEqualTo("203.0.113.7");
        assertThat(s.getDevice()).isEqualTo("Chrome · Windows");
    }

    @Test
    @DisplayName("不带来源信息签发时，IP/设备为 null 而不是报错")
    void sessionMetaIsOptional() {
        store.issue(user(1L, "admin", "超级管理员"));

        ActiveSession s = store.listActive().get(0);
        assertThat(s.getIp()).isNull();
        assertThat(s.getDevice()).isNull();
    }

    @Test
    @DisplayName("刷新令牌后，新会话仍带着原登录的 IP 与设备")
    void refreshCarriesSessionMetaForward() {
        IssuedTokens issued = store.issueWithRefresh(user(1L, "admin", "超级管理员"),
                new SessionMeta("203.0.113.7", "Firefox · Linux"));

        store.refresh(issued.getRefreshToken()).orElseThrow();

        ActiveSession s = store.listActive().stream()
                .filter(x -> "203.0.113.7".equals(x.getIp()))
                .findFirst().orElseThrow();
        assertThat(s.getDevice()).isEqualTo("Firefox · Linux");
    }

    @Test
    @DisplayName("最近登录的排在前面")
    void sortedByIssuedAtDescending() throws Exception {
        store.issue(user(1L, "first", "先登录"));
        Thread.sleep(10);
        store.issue(user(2L, "second", "后登录"));

        assertThat(store.listActive())
                .extracting(ActiveSession::getUsername)
                .containsExactly("second", "first");
    }
}
