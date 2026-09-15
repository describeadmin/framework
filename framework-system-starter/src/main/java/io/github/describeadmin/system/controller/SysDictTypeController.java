package io.github.describeadmin.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.entity.SysDictType;
import io.github.describeadmin.system.mapper.SysDictTypeMapper;
import io.github.describeadmin.system.service.SysDictTypeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    /**
     * 列表查询的筛选条件，见 {@link SysUserController#buildListWrapper} 同一处理方式。
     *
     * <p>{@code keyword} 是前端字典管理页单搜索框用的组合条件——同时对 {@code dict_name}
     * 与 {@code dict_type} 做 OR 匹配，避免页面并排放两个输入框在窄屏下挤成一堆。
     * {@code dictName}/{@code dictType} 两个精确字段保留，供业务方需要分别过滤时使用，
     * 与 {@code keyword} 可以同时传、叠加为 AND。
     */
    @Override
    protected Wrapper<SysDictType> buildListWrapper(Map<String, String> params) {
        QueryWrapper<SysDictType> wrapper = new QueryWrapper<>();
        String keyword = text(params, "keyword");
        if (keyword != null) {
            wrapper.and(w -> w.likeRight("dict_name", keyword).or().likeRight("dict_type", keyword));
        }
        wrapper.likeRight(text(params, "dictName") != null, "dict_name", text(params, "dictName"));
        wrapper.likeRight(text(params, "dictType") != null, "dict_type", text(params, "dictType"));
        wrapper.eq(asInt(params, "status") != null, "status", asInt(params, "status"));
        return wrapper;
    }
}
