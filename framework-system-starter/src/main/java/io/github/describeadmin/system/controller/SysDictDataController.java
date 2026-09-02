package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDictData;
import io.github.describeadmin.system.mapper.SysDictDataMapper;
import io.github.describeadmin.system.service.SysDictDataService;
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

    /**
     * 按字典类型取全部启用中的字典项，供前端下拉框使用。
     *
     * <p>仅需登录、不挂具体权限点：字典项是跨页面共享的枚举数据（只返回启用中的项），
     * 若要求 {@code system:dict:list}，每个用到字典下拉框的角色都得被授予"字典管理"
     * 权限——侧边栏因此多出不该有的管理入口。过滤链的 {@code anyRequest().authenticated()}
     * 已兜底，未登录仍拿不到。
     */
    @GetMapping("/type/{dictType}")
    public Result<List<SysDictData>> byType(@PathVariable String dictType) {
        return Result.ok(service.listByType(dictType));
    }
}
