package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.describeadmin.mybatis.api.BaseEntity;

/** 用户。 */
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    /**
     * 密码哈希（BCrypt）。
     *
     * <p>{@code @JsonIgnore} 保证它永远不会出现在任何 JSON 响应里——
     * BaseController 的通用 CRUD 端点会直接序列化实体，没有这个注解就会泄露密码哈希。
     */
    @JsonIgnore
    private String password;

    private String nickname;
    private Long deptId;
    private Integer status;

    /** 非持久化字段：仅用于向前端返回部门名称。 */
    @TableField(exist = false)
    private String deptName;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
