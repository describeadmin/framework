package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
     *
     * <p>{@code updateStrategy = NOT_NULL}：本项目未全局配置 update-strategy，
     * MyBatis-Plus 默认按字段实际值生成 UPDATE 语句——字段是 null 就会把该列显式置为
     * NULL（{@code SysDeptService.updateDept} 每次都要重新赋值 {@code ancestors}
     * 正是因为这个默认行为）。密码字段被 {@code @JsonIgnore} 之后，任何走通用
     * {@code updateById} 的编辑请求反序列化出来的 entity.password 必然是 null——
     * 不锁住这个字段，编辑用户时会把密码哈希整列覆盖成 NULL，直接把这个账号锁死。
     */
    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String password;

    private String nickname;
    private String mobile;
    private String email;
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
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
