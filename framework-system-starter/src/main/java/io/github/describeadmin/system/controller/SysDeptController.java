package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.mapper.SysDeptMapper;
import io.github.describeadmin.system.service.SysDeptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部门管理。继承 BaseController 获得标准 CRUD，只补充树形查询。 */
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

    @GetMapping("/tree")
    public Result<List<SysDept>> tree() {
        return Result.ok(service.tree());
    }
}
