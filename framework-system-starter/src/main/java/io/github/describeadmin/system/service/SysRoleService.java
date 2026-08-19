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
}
