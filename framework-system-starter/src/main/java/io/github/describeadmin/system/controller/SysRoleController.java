package io.github.describeadmin.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.core.OperLog;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import io.github.describeadmin.system.service.SysRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    /**
     * 列表查询的筛选条件，见 {@link SysUserController#buildListWrapper} 同一处理方式。
     *
     * <p>按 {@code sort} 升序，与部门/菜单/字典数据一致（那三处在各自 Service 里排）。
     * {@code sort} 不只是展示顺序：它决定多角色用户的默认首页由哪个角色胜出
     * （见 {@code HomePathResolver}），列表不按它排会让"排序"这一列看起来毫无作用。
     *
     * <p>{@code id} 是二级排序，不能省——{@code sort} 允许重复（默认值 0），
     * 只按它排时同值行的相对顺序由数据库自行决定，<b>翻页会重复或漏掉记录</b>。
     */
    @Override
    protected Wrapper<SysRole> buildListWrapper(Map<String, String> params) {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.likeRight(text(params, "roleName") != null, "role_name", text(params, "roleName"));
        wrapper.likeRight(text(params, "roleCode") != null, "role_code", text(params, "roleCode"));
        wrapper.orderByAsc("sort", "id");
        return wrapper;
    }

    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping("/{roleId}/menus")
    public Result<List<Long>> menus(@PathVariable Long roleId) {
        return Result.ok(service.menuIdsOf(roleId));
    }

    /** 重新授予菜单权限。整体覆盖而非增量修改，与授权的"重建"语义一致。 */
    @OperLog(module = "system:role", description = "分配菜单")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @PutMapping("/{roleId}/menus")
    public Result<Void> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        service.assignMenus(roleId, menuIds);
        return Result.ok();
    }

    /**
     * 该角色自定义数据权限的部门列表。{@code data_scope} 本身是 {@link SysRole} 上的
     * 普通字段，走通用的 {@code update()} 端点即可，不需要单独接口。
     */
    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping("/{roleId}/depts")
    public Result<List<Long>> depts(@PathVariable Long roleId) {
        return Result.ok(service.deptIdsOf(roleId));
    }

    /** 重新指定自定义数据权限的部门列表。整体覆盖，与 {@link #assignMenus} 同一语义。 */
    @OperLog(module = "system:role", description = "分配数据权限")
    @PreAuthorize("hasAuthority('system:role:assign-dept')")
    @PutMapping("/{roleId}/depts")
    public Result<Void> assignDepts(@PathVariable Long roleId, @RequestBody List<Long> deptIds) {
        service.assignDataScopeDepts(roleId, deptIds);
        return Result.ok();
    }
}
