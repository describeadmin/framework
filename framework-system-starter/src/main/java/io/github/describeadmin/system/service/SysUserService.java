package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
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

    public SysUserService(SysRelationMapper relationMapper, PasswordEncoder passwordEncoder) {
        this.relationMapper = relationMapper;
        this.passwordEncoder = passwordEncoder;
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

    /** 重置密码。入参是明文，存储的是哈希。 */
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
