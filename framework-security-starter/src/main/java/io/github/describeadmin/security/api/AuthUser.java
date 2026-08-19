package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证用的用户数据，由 {@link AuthUserLoader} 从业务方的存储中加载。
 *
 * <p>与 {@link LoginUser} 的区别：本类含密码哈希，只在认证过程中使用，
 * 认证通过后转换为不含凭据的 {@link LoginUser} 对外流通。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class AuthUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;

    /** 密码哈希（如 BCrypt），<b>不是</b>明文。 */
    private final String passwordHash;

    private final String nickname;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<String> permissions;

    public AuthUser(Long userId, String username, String passwordHash, String nickname,
                    boolean enabled, Set<String> roles, Set<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.enabled = enabled;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
    }

    private static Set<String> immutable(Set<String> src) {
        return src == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(src));
    }

    /** 转换为对外流通的登录用户，<b>丢弃密码哈希</b>。 */
    public LoginUser toLoginUser(String authType) {
        return new LoginUser(userId, username, nickname, authType, roles, permissions);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
