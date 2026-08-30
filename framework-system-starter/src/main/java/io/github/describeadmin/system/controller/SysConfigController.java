package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysConfig;
import io.github.describeadmin.system.mapper.SysConfigMapper;
import io.github.describeadmin.system.service.SysConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统参数配置管理。缓存失效在 {@link SysConfigService} 里，本类不用关心。 */
@RestController
@RequestMapping("/api/system/config")
public class SysConfigController extends BaseController<SysConfigService, SysConfigMapper, SysConfig> {

    private final SysConfigService service;

    public SysConfigController(SysConfigService service) {
        this.service = service;
    }

    @Override
    protected SysConfigService getService() {
        return service;
    }

    /** 按 key 取值，权限点复用 list。 */
    @PreAuthorize("hasAuthority('system:config:list')")
    @GetMapping("/key/{configKey}")
    public Result<String> byKey(@PathVariable String configKey) {
        return Result.ok(service.getValue(configKey));
    }
}
