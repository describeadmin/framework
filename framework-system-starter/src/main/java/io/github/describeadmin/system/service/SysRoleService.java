package io.github.describeadmin.system.service;

import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 角色管理。 */
@Service
public class SysRoleService extends BaseService<SysRoleMapper, SysRole> {

    private final SysRelationMapper relationMapper;

    public SysRoleService(SysRelationMapper relationMapper) {
        this.relationMapper = relationMapper;
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
