package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证通过后的用户主体。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String nickname;

    /** 认证来源，取值为对应 {@link AuthProvider#type()}。 */
    private final String authType;

    private final Set<String> roles;
    private final Set<String> permissions;

    public LoginUser(Long userId, String username, String nickname, String authType,
                     Set<String> roles, Set<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.authType = authType;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
    }

    private static Set<String> immutable(Set<String> src) {
        return src == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(src));
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAuthType() {
        return authType;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
