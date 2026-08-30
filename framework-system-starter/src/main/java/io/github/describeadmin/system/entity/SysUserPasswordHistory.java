package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户历史密码。
 *
 * <p><b>不继承 {@link io.github.describeadmin.mybatis.api.BaseEntity}</b>：只追加、不给用户改，
 * 与 {@link SysOperLog} 同理。写入完全由 {@code SysUserService} 的三处设密码路径控制。
 *
 * <p>仅当 {@code sys.password.history-count > 0} 时参与校验：新密码不得命中该用户最近 N 条历史。
 */
@TableName("sys_user_password_history")
public class SysUserPasswordHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long userId;

    /** 历史密码的 BCrypt 哈希。 */
    private String passwordHash;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
