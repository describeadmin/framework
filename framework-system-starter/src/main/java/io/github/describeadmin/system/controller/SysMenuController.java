package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.mapper.SysMenuMapper;
import io.github.describeadmin.system.service.SysMenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 菜单管理。 */
@RestController
@RequestMapping("/api/system/menu")
public class SysMenuController extends BaseController<SysMenuService, SysMenuMapper, SysMenu> {

    private final SysMenuService service;

    public SysMenuController(SysMenuService service) {
        this.service = service;
    }

    @Override
    protected SysMenuService getService() {
        return service;
    }

    /** 全量菜单树，管理端配置界面使用。 */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.ok(service.tree());
    }
}
