package io.github.describeadmin.system.controller;

import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDictType;
import io.github.describeadmin.system.mapper.SysDictTypeMapper;
import io.github.describeadmin.system.service.SysDictTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型管理。
 *
 * <p>与 {@link SysDictDataController} 共用权限前缀 {@code system:dict}——两者是同一个
 * 管理页面的两个面板，不需要分开的权限对象，见两个类都覆写的 {@code permPrefix()}。
 */
@RestController
@RequestMapping("/api/system/dict/type")
public class SysDictTypeController
        extends BaseController<SysDictTypeService, SysDictTypeMapper, SysDictType> {

    private final SysDictTypeService service;

    public SysDictTypeController(SysDictTypeService service) {
        this.service = service;
    }

    @Override
    protected SysDictTypeService getService() {
        return service;
    }

    @Override
    public String permPrefix() {
        return "system:dict";
    }
}
