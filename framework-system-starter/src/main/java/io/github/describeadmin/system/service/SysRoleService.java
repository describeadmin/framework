package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.UniqueGuard;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理。
 *
 * <p>{@code roleCode} 唯一性在应用层校验，而非数据库唯一索引——逻辑删除下建唯一索引
 * 会导致删除后无法复用同 code 角色，理由同 {@code SysUserService.username}（见
 * schema-rbac.sql 对应注释）。<b>并发唯一性由 {@link UniqueGuard} 的键级锁保证</b>。
 *
 * <p>角色名（{@code roleName}）是展示字段，允许重名——两个角色都叫"运营"是合法的，
 * 权限授予与引用走 {@code roleCode}。这一点与用户名不同：用户名是登录凭证，必须唯一。
 */
@Service
public class SysRoleService extends BaseService<SysRoleMapper, SysRole> {

    private final SysRelationMapper relationMapper;
    private final UniqueGuard uniqueGuard;

    public SysRoleService(SysRelationMapper relationMapper, UniqueGuard uniqueGuard) {
        this.relationMapper = relationMapper;
        this.uniqueGuard = uniqueGuard;
    }

    /**
     * 校验角色标识在「未删除」范围内未被其他记录占用。
     *
     * <p>{@code selfId} 语义同 {@code SysDictTypeService}：新建传 {@code null}，
     * 编辑传当前 id，排除"改别的字段但 code 没变"的误判。
     */
    private void assertRoleCodeAvailable(Long selfId, String roleCode) {
        SysRole exist = getOne(new QueryWrapper<SysRole>().eq("role_code", roleCode), false);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色标识已存在: " + roleCode);
        }
    }

    @Override
    public boolean save(SysRole entity) {
        return uniqueGuard.execute("sys:role:code", entity.getRoleCode(), () -> {
            assertRoleCodeAvailable(null, entity.getRoleCode());
            return super.save(entity);
        });
    }

    @Override
    public boolean updateById(SysRole entity) {
        return uniqueGuard.execute("sys:role:code", entity.getRoleCode(), () -> {
            if (entity.getRoleCode() != null) {
                assertRoleCodeAvailable(entity.getId(), entity.getRoleCode());
            }
            return super.updateById(entity);
        });
    }

    /** 重新授予菜单权限：同样是"重建"语义。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        relationMapper.deleteRoleMenus(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            relationMapper.insertRoleMenus(roleId, menuIds);
        }
    }

    public List<Long> menuIdsOf(Long roleId) {
        return relationMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 重新指定自定义数据权限的部门列表，同样是"重建"语义。
     *
     * <p>只在角色的 {@code dataScope = CUSTOM} 时有意义；对其余档位的角色调用本方法
     * 不会报错，但写入的部门列表在数据权限拦截器里不会被读取（见
     * {@code DeptDataPermissionHandler}），调用方自行保证 {@code data_scope} 已经是 CUSTOM。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignDataScopeDepts(Long roleId, List<Long> deptIds) {
        relationMapper.deleteRoleDepts(roleId);
        if (deptIds != null && !deptIds.isEmpty()) {
            relationMapper.insertRoleDepts(roleId, deptIds);
        }
    }

    public List<Long> deptIdsOf(Long roleId) {
        return relationMapper.selectDeptIdsByRoleId(roleId);
    }
}
