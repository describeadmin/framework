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
     * 创建用户。
     *
     * <p>用户名唯一性在应用层校验而非数据库唯一索引——逻辑删除下建唯一索引会导致
     * 删除后无法复用同名账号（见 schema-rbac.sql 的注释）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(SysUser user, String rawPassword, List<Long> roleIds) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在: " + user.getUsername());
        }
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
