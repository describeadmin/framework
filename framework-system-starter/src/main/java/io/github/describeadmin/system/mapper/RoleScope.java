package io.github.describeadmin.system.mapper;

/**
 * {@code sys_role.data_scope} 的最小投影，供 {@code DataScopeResolver} 合并多角色范围用。
 *
 * <p>不用 {@code SysRole} 整个实体接：这条查询只需要两列，选完整实体会让人误以为
 * 其余字段（如 {@code roleName}）也有值，实际都是 null。
 */
public class RoleScope {

    private Long roleId;
    private Integer dataScope;

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
}
