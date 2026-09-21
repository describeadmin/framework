package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.OptionItem;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.mapper.SysDeptMapper;
import io.github.describeadmin.system.service.SysDeptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理。继承 BaseController 获得标准 CRUD，但覆写 create/update——
 * {@code ancestors} 是派生自父部门的物化路径，不能让调用方直接摆布，
 * 必须经 {@link SysDeptService#createDept}/{@link SysDeptService#updateDept} 计算。
 */
@RestController
@RequestMapping("/api/system/dept")
public class SysDeptController extends BaseController<SysDeptService, SysDeptMapper, SysDept> {

    private final SysDeptService service;

    public SysDeptController(SysDeptService service) {
        this.service = service;
    }

    @Override
    protected SysDeptService getService() {
        return service;
    }

    @Override
    @PreAuthorize("hasAuthority('system:dept:add')")
    @PostMapping
    public Result<SysDept> create(@RequestBody SysDept entity) {
        return Result.ok(service.createDept(entity));
    }

    @Override
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @PutMapping("/{id}")
    public Result<SysDept> update(@PathVariable Long id, @RequestBody SysDept entity) {
        return Result.ok(service.updateDept(id, entity));
    }

    @PreAuthorize("hasAuthority('system:dept:list')")
    @GetMapping("/tree")
    public Result<List<SysDept>> tree() {
        return Result.ok(service.tree());
    }

    /**
     * 部门选项树，供别的模块的选择器使用（用户管理的「部门」、角色管理的「分配数据权限」）。
     *
     * <p><b>仅需登录、不挂权限点</b>，理由与分档见
     * {@link SysRoleController#options} 与 CLAUDE.md 4.11。{@link #tree()} 保留不动——
     * 部门管理页自己用它，那是同模块访问，本来就闭环。
     *
     * <p>两者的差别是形状不是内容：本端点只下发 id + 名称 + 禁用态，
     * 不含 {@code leader}/{@code phone}/{@code ancestors}。
     */
    @GetMapping("/options")
    public Result<List<OptionItem>> options() {
        return Result.ok(service.listOptions());
    }
}
