package io.github.describeadmin.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.OptionItem;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.core.OperLog;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import io.github.describeadmin.system.service.SysMenuService;
import io.github.describeadmin.system.service.SysRoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 角色管理。 */
@RestController
@RequestMapping("/api/system/role")
public class SysRoleController extends BaseController<SysRoleService, SysRoleMapper, SysRole> {

    private final SysRoleService service;
    private final SysMenuService menuService;

    public SysRoleController(SysRoleService service, SysMenuService menuService) {
        this.service = service;
        this.menuService = menuService;
    }

    @Override
    protected SysRoleService getService() {
        return service;
    }

    /**
     * 角色选项，供别的模块的选择器使用（用户管理的「角色」下拉、「分配角色」弹窗）。
     *
     * <p><b>仅需登录、不挂权限点</b>，范式同 {@link SysDictDataController#byType}：
     * 若要求 {@code system:role:list}，每个需要「选个角色」的角色都得被授予"角色管理"权限，
     * 侧边栏因此多出不该有的管理入口；不授又会 403。这两个结果都不是想要的，
     * 而且<b>换个权限点也解决不了</b>——权限点要能被授予就得登记进 {@code sys_menu}，
     * 而角色授权对祖先做闭包（{@code SysRoleService.assignMenus}），
     * 授子必授父，侧边栏入口照样冒出来。完整约定与分档见 CLAUDE.md 4.11。
     *
     * <p>下发的只有 id 与角色名，不含 {@code roleCode}/{@code dataScope} 等授权内部结构。
     * 过滤链的 {@code anyRequest().authenticated()} 已兜底，未登录仍拿不到。
     *
     * @param keyword 角色名前缀，可选；不传则返回全量，超过 {@code OptionItem.MAX_SIZE} 时报错
     */
    @GetMapping("/options")
    public Result<List<OptionItem>> options(@RequestParam(required = false) String keyword) {
        return Result.ok(service.listOptions(keyword));
    }

    /**
     * 可授予的完整菜单树，供「分配菜单」弹窗与「首页」选择器使用。
     *
     * <p><b>这个端点开在角色模块而不是菜单模块，是本方案的关键一步。</b>
     * 分配菜单是<b>角色管理自己的功能</b>，它需要的数据就该由角色模块提供、按角色模块的
     * 权限点校验；过去去调 {@code GET /api/system/menu/tree} 则要求 {@code system:menu:list}，
     * 于是"能管角色"和"能看菜单管理"被迫捆绑。判据见 CLAUDE.md 4.11：
     * 通用 {@code OptionItem} 形状够用就走 {@code /options}（仅登录），
     * 不够用就由宿主模块开端点——这里需要 {@code menuType}/{@code path} 判断哪些节点可选，
     * {@code OptionItem} 覆盖不了，所以走后者。
     *
     * <p>权限点取 {@code system:role:list} 而非 {@code :edit}，与同为读操作的
     * {@link #menus} / {@link #depts} 一致：打开角色管理页就该看得到这棵树，
     * 能不能提交由 {@link #assignMenus} 自己把关。
     *
     * <p>返回<b>含按钮</b>的全量树——授权树必须能勾到 BUTTON 级权限点。
     */
    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping("/grantable-menus")
    public Result<List<SysMenu>> grantableMenus() {
        return Result.ok(menuService.tree());
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
