package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

/** 角色。 */
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private Integer sort;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
