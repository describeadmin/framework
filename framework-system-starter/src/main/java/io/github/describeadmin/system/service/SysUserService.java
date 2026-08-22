package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 用户管理。 */
@Service
public class SysUserService extends BaseService<SysUserMapper, SysUser> {

    private final SysRelationMapper relationMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public SysUserService(SysRelationMapper relationMapper, PasswordEncoder passwordEncoder, TokenStore tokenStore) {
        this.relationMapper = relationMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    public SysUser findByUsername(String username) {
        // 逻辑删除由 @TableLogic 自动过滤，这里无需再写 deleted = 0
        return getOne(new QueryWrapper<SysUser>().eq("username", username), false);
    }

    /**
     * 按手机号查询。供手机验证码登录等 {@code AuthProvider} 插件把凭证换成 userId 用——
     * 插件不需要自建映射表，手机号本身就是核心字段，直接查这里即可，
     * 拿到 userId 后再调 {@code AuthUserLoader.loadByUserId} 拼出完整用户。
     */
    public SysUser findByMobile(String mobile) {
        return getOne(new QueryWrapper<SysUser>().eq("mobile", mobile), false);
    }

    /** 按邮箱查询，用途同 {@link #findByMobile(String)}。 */
    public SysUser findByEmail(String email) {
        return getOne(new QueryWrapper<SysUser>().eq("email", email), false);
    }

    /**
     * 校验手机号/邮箱未被其他未删除用户占用，非空时才校验。
     *
     * <p>{@code selfId} 是当前正在创建/编辑的用户自身 id：新建时传 {@code null}
     * （不存在"自己"），编辑时传当前 userId，排除"改别的字段但手机号没变"这种误判。
     * 逻辑删除下不能建唯一索引（同 username，见 schema-rbac.sql 注释），唯一性只能在这里保证。
     */
    public void assertMobileEmailAvailable(Long selfId, String mobile, String email) {
        if (StringUtils.hasText(mobile)) {
            SysUser exist = findByMobile(mobile);
            if (exist != null && !exist.getId().equals(selfId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "手机号已被占用: " + mobile);
            }
        }
        if (StringUtils.hasText(email)) {
            SysUser exist = findByEmail(email);
            if (exist != null && !exist.getId().equals(selfId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "邮箱已被占用: " + email);
            }
        }
    }

    /**
     * 创建用户。
     *
     * <p>用户名唯一性在应用层校验而非数据库唯一索引——逻辑删除下建唯一索引会导致
     * 删除后无法复用同名账号（见 schema-rbac.sql 的注释）。手机号/邮箱同理，见
     * {@link #assertMobileEmailAvailable(Long, String, String)}。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(SysUser user, String rawPassword, List<Long> roleIds) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在: " + user.getUsername());
        }
        assertMobileEmailAvailable(null, user.getMobile(), user.getEmail());
        if (!StringUtils.hasText(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "初始密码不能为空");
        }
        user.setId(null);
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        save(user);
        assignRoles(user.getId(), roleIds);
        return user;
    }

    /**
     * 重置密码（管理员改别人）。入参是明文，存储的是哈希。
     *
     * <p>改密后立即吊销该用户已签发的全部令牌——不这样做的话，被改密的用户
     * 已登录的会话仍然有效，要等令牌自然过期才失效，不符合"改密立即失效"的直觉预期
     * （见 {@link TokenStore#revokeAllOf(Long)} 的 javadoc）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        SysUser exist = getById(userId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setVersion(exist.getVersion());
        update.setPassword(passwordEncoder.encode(rawPassword));
        updateById(update);
        tokenStore.revokeAllOf(userId);
    }

    /**
     * 自助改密（当前登录用户改自己的密码）。
     *
     * <p>与 {@link #resetPassword(Long, String)} 的区别：这里要校验旧密码，
     * 而不是管理员凭权限直接覆盖。成功后同样吊销全部令牌——本次修改所在的这一个
     * 请求令牌也会被吊销，前端应据此引导用户用新密码重新登录，而不是指望原地继续用旧令牌。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(Long userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        SysUser exist = getById(userId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, exist.getPassword())) {
            throw new BizException(ResultCode.AUTH_FAILED, "原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setVersion(exist.getVersion());
        update.setPassword(passwordEncoder.encode(newPassword));
        updateById(update);
        tokenStore.revokeAllOf(userId);
    }

    /** 重新授予角色：先清空再插入，是"重建"而非"增量修改"。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        relationMapper.deleteUserRoles(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            relationMapper.insertUserRoles(userId, roleIds);
        }
    }

    public List<Long> roleIdsOf(Long userId) {
        return relationMapper.selectRoleIdsByUserId(userId);
    }
}
