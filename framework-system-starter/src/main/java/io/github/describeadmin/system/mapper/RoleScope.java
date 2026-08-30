package io.github.describeadmin.system.mapper;

/**
 * {@code sys_role} 的最小投影，供 {@code DataScopeResolver}/{@code HomePathResolver}
 * 合并多角色的数据权限范围与默认首页用。
 *
 * <p>不用 {@code SysRole} 整个实体接：这条查询只需要三列，选完整实体会让人误以为
 * 其余字段（如 {@code roleName}）也有值，实际都是 null。
 */
public class RoleScope {

    private Long roleId;
    private Integer dataScope;
    private String homePath;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getDataScope() {
        return dataScope;
    }

    public void setDataScope(Integer dataScope) {
        this.dataScope = dataScope;
    }

    public String getHomePath() {
        return homePath;
    }

    public void setHomePath(String homePath) {
        this.homePath = homePath;
    }
}
