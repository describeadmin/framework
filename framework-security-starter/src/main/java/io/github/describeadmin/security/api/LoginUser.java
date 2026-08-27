package io.github.describeadmin.security.api;

import io.github.describeadmin.common.api.DataScopeType;

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

    /** 所属部门 ID；数据权限的 {@code DEPT}/{@code DEPT_AND_CHILD} 档据此生效，可为 null。 */
    private final Long deptId;

    /** 全部角色合并后的有效数据权限范围，见 {@link DataScopeType}。 */
    private final DataScopeType dataScope;

    /** {@code dataScope} 为 {@link DataScopeType#CUSTOM} 时的部门 ID 集合，其余档为空集。 */
    private final Set<Long> customDeptIds;

    /**
     * 全部角色合并后的默认首页路径，{@code null} 表示没有任何角色设置过，
     * 由前端落回全局 {@code preferences.app.defaultHomePath}。
     */
    private final String homePath;

    /**
     * 是否要求强制修改密码（管理员建号 / 重置密码后，或密码已过有效期）。
     *
     * <p>为 true 时 {@code PasswordResetRequiredFilter} 只放行 {@code PUT /api/auth/password}、
     * {@code GET /api/auth/me}、{@code POST /api/auth/logout}，其余请求返回
     * {@code ResultCode.PASSWORD_RESET_REQUIRED}。前端据此跳强制改密页。
     */
    private final boolean pwdResetRequired;

    public LoginUser(Long userId, String username, String nickname, String authType,
                     Set<String> roles, Set<String> permissions) {
        this(userId, username, nickname, authType, roles, permissions,
                null, DataScopeType.ALL, Set.of(), null);
    }

    public LoginUser(Long userId, String username, String nickname, String authType,
                     Set<String> roles, Set<String> permissions,
                     Long deptId, DataScopeType dataScope, Set<Long> customDeptIds) {
        this(userId, username, nickname, authType, roles, permissions,
                deptId, dataScope, customDeptIds, null);
    }

    public LoginUser(Long userId, String username, String nickname, String authType,
                     Set<String> roles, Set<String> permissions,
                     Long deptId, DataScopeType dataScope, Set<Long> customDeptIds, String homePath) {
        this(userId, username, nickname, authType, roles, permissions,
                deptId, dataScope, customDeptIds, homePath, false);
    }

    public LoginUser(Long userId, String username, String nickname, String authType,
                     Set<String> roles, Set<String> permissions,
                     Long deptId, DataScopeType dataScope, Set<Long> customDeptIds, String homePath,
                     boolean pwdResetRequired) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.authType = authType;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
        this.deptId = deptId;
        this.dataScope = dataScope == null ? DataScopeType.ALL : dataScope;
        this.customDeptIds = immutableLong(customDeptIds);
        this.homePath = homePath;
        this.pwdResetRequired = pwdResetRequired;
    }

    private static Set<String> immutable(Set<String> src) {
        return src == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(src));
    }

    private static Set<Long> immutableLong(Set<Long> src) {
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

    public Long getDeptId() {
        return deptId;
    }

    public DataScopeType getDataScope() {
        return dataScope;
    }

    public Set<Long> getCustomDeptIds() {
        return customDeptIds;
    }

    public String getHomePath() {
        return homePath;
    }

    public boolean isPwdResetRequired() {
        return pwdResetRequired;
    }
}
