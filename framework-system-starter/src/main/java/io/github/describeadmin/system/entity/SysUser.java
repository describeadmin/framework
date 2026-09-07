package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.describeadmin.mybatis.api.BaseEntity;

import java.time.LocalDateTime;

/** 用户。 */
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 用户名 = 账号标识，<b>一经创建不可修改</b>。
     *
     * <p>{@code updateStrategy = NEVER}：任何 UPDATE 都不带这一列，改在数据层堵死，
     * 而不是靠每个调用方自觉。它防的是"前端表单出意外"这一类事故：
     * 通用编辑端点把请求体整个反序列化成实体再 {@code updateById}，请求体里出现
     * {@code username} 就会落库，两种后果都不可接受——
     * <ul>
     *   <li>改成别的值：账号标识被换掉，登录名与操作日志/历史记录全部对不上，
     *       且绕过了创建时才做的用户名唯一性校验（编辑路径根本没有那道校验）</li>
     *   <li>改成空串：空串不是 null，挡不住下面说的 NOT_NULL 策略，会真的写进库——
     *       这个账号从此再也登录不上，而全过程没有任何报错</li>
     * </ul>
     *
     * <p>为什么不是靠 {@code null} 兜底：MyBatis-Plus 全局 {@code updateStrategy}
     * 默认 {@code NOT_NULL}（已用 {@code javap} 核对 3.5.17 的 {@code GlobalConfig$DbConfig}
     * 构造器字节码），只挡得住 null，挡不住空串和真实的新值。
     *
     * <p>确有改用户名需求时，走"新建账号 + 停用旧账号"，不要放开这一列——
     * 用户名是很多外部系统对接时的关联键。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String username;

    /**
     * 密码哈希（BCrypt）。
     *
     * <p>{@code @JsonIgnore} 保证它永远不会出现在任何 JSON 响应里——
     * BaseController 的通用 CRUD 端点会直接序列化实体，没有这个注解就会泄露密码哈希。
     *
     * <p>{@code updateStrategy = NOT_NULL}：密码字段被 {@code @JsonIgnore} 之后，
     * 任何走通用 {@code updateById} 的编辑请求反序列化出来的 entity.password 必然是 null，
     * 落库就会把密码哈希整列覆盖成 NULL，直接把账号锁死。MyBatis-Plus 的全局默认
     * 恰好也是 {@code NOT_NULL}（已核对 3.5.17 字节码），所以这行注解在默认配置下是
     * 冗余的——留着是因为它防的是<b>业务方把全局 update-strategy 改成 ALWAYS</b>：
     * 字段级策略优先于全局，改全局配置不会把这里悄悄放开。
     *
     * <p>注意 {@code NOT_NULL} 只挡 null，空串照样落库；要完全不参与 UPDATE 用
     * {@code NEVER}（见上面的 {@link #username}）。密码这里不能用 NEVER——
     * {@code resetPassword}/{@code changeOwnPassword} 需要真的写这一列。
     */
    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.NOT_NULL)
    private String password;

    private String nickname;
    private String mobile;
    private String email;
    private Long deptId;
    private Integer status;

    /**
     * 下次登录必须改密：1是 0否。管理员建号 / 重置密码后置 1，用户自助改密成功后清 0。
     * 密码「定期过期」不写这一列（登录时按 {@link #pwdUpdateTime} 算），见
     * {@code io.github.describeadmin.system.core.DbAuthUserLoader}。
     */
    private Integer pwdResetRequired;

    /** 密码最后修改时间，供「定期强制过期」判断。为 null（旧库升级）时按「未过期」处理。 */
    private LocalDateTime pwdUpdateTime;

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
    public Integer getPwdResetRequired() { return pwdResetRequired; }
    public void setPwdResetRequired(Integer pwdResetRequired) { this.pwdResetRequired = pwdResetRequired; }
    public LocalDateTime getPwdUpdateTime() { return pwdUpdateTime; }
    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) { this.pwdUpdateTime = pwdUpdateTime; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
