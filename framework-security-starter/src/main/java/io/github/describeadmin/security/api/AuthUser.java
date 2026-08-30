package io.github.describeadmin.security.api;

import io.github.describeadmin.common.api.DataScopeType;

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
     * 是否要求本次登录后强制修改密码。
     *
     * <p>true 的两种来源：{@code sys_user.pwd_reset_required = 1}（管理员建号 / 重置密码后），
     * 或密码已超过 {@code sys.password.max-age-days} 设定的有效期（登录时算出）。
     * 转成 {@link LoginUser} 后由 {@code PasswordResetRequiredFilter} 拦截，
     * 只放行改密 / me / 登出。
     */
    private final boolean pwdResetRequired;

    public AuthUser(Long userId, String username, String passwordHash, String nickname,
                    boolean enabled, Set<String> roles, Set<String> permissions) {
        this(userId, username, passwordHash, nickname, enabled, roles, permissions,
                null, DataScopeType.ALL, Set.of(), null);
    }

    public AuthUser(Long userId, String username, String passwordHash, String nickname,
                    boolean enabled, Set<String> roles, Set<String> permissions,
                    Long deptId, DataScopeType dataScope, Set<Long> customDeptIds) {
        this(userId, username, passwordHash, nickname, enabled, roles, permissions,
                deptId, dataScope, customDeptIds, null);
    }

    public AuthUser(Long userId, String username, String passwordHash, String nickname,
                    boolean enabled, Set<String> roles, Set<String> permissions,
                    Long deptId, DataScopeType dataScope, Set<Long> customDeptIds, String homePath) {
        this(userId, username, passwordHash, nickname, enabled, roles, permissions,
                deptId, dataScope, customDeptIds, homePath, false);
    }

    public AuthUser(Long userId, String username, String passwordHash, String nickname,
                    boolean enabled, Set<String> roles, Set<String> permissions,
                    Long deptId, DataScopeType dataScope, Set<Long> customDeptIds, String homePath,
                    boolean pwdResetRequired) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.enabled = enabled;
        this.roles = immutable(roles);
        this.permissions = immutable(permissions);
        this.deptId = deptId;
        this.dataScope = dataScope == null ? DataScopeType.ALL : dataScope;
        this.customDeptIds = immutableLong(customDeptIds);
        this.homePath = homePath;
        this.pwdResetRequired = pwdResetRequired;
    }

    private static Set<String> immutable(Set<String> src) {
        return src == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(src));
    }

    private static Set<Long> immutableLong(Set<Long> src) {
        return src == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(src));
    }

    /** 转换为对外流通的登录用户，<b>丢弃密码哈希</b>。 */
    public LoginUser toLoginUser(String authType) {
        return new LoginUser(userId, username, nickname, authType, roles, permissions,
                deptId, dataScope, customDeptIds, homePath, pwdResetRequired);
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
