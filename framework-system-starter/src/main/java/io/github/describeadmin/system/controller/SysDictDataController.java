package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDictData;
import io.github.describeadmin.system.mapper.SysDictDataMapper;
import io.github.describeadmin.system.service.SysDictDataService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 字典数据管理。缓存失效在 {@link SysDictDataService} 里，本类不用关心。 */
@RestController
@RequestMapping("/api/system/dict/data")
public class SysDictDataController
        extends BaseController<SysDictDataService, SysDictDataMapper, SysDictData> {

    private final SysDictDataService service;

    public SysDictDataController(SysDictDataService service) {
        this.service = service;
    }

    @Override
    protected SysDictDataService getService() {
        return service;
    }

    @Override
    public String permPrefix() {
        return "system:dict";
    }

    /** 按字典类型取全部启用中的字典项，供前端下拉框使用。权限点复用 list。 */
    @PreAuthorize("hasAuthority('system:dict:list')")
    @GetMapping("/type/{dictType}")
    public Result<List<SysDictData>> byType(@PathVariable String dictType) {
        return Result.ok(service.listByType(dictType));
    }
}
