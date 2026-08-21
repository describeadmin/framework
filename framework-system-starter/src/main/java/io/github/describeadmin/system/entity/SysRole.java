package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

/** 角色。 */
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private Integer sort;

    /**
     * 数据权限范围，取值见 {@code DataScopeType.getCode()}（1 全部 / 2 自定义部门 /
     * 3 本部门 / 4 本部门及以下 / 5 仅本人）。存成 {@code Integer} 而非绑定
     * MyBatis-Plus 的 {@code IEnum}——仓库里现有实体的枚举类字段都是这个写法，
     * 换一套范式收益不足以抵消不一致的代价。业务层用
     * {@code DataScopeType.ofCode(role.getDataScope())} 转换。
     */
    private Integer dataScope;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }
}
