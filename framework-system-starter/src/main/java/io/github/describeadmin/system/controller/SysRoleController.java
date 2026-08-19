package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import io.github.describeadmin.system.service.SysRoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色管理。 */
@RestController
@RequestMapping("/api/system/role")
public class SysRoleController extends BaseController<SysRoleService, SysRoleMapper, SysRole> {

    private final SysRoleService service;

    public SysRoleController(SysRoleService service) {
        this.service = service;
    }

    @Override
    protected SysRoleService getService() {
        return service;
    }

    @GetMapping("/{roleId}/menus")
    public Result<List<Long>> menus(@PathVariable Long roleId) {
        return Result.ok(service.menuIdsOf(roleId));
    }

    /** 重新授予菜单权限。整体覆盖而非增量修改，与授权的"重建"语义一致。 */
    @PutMapping("/{roleId}/menus")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        service.assignMenus(roleId, menuIds);
        return Result.ok();
    }
}
