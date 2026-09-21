package io.github.describeadmin.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.Map;

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
     * 列表查询的筛选条件。
     *
     * <p><b>在服务端按 {@code dictType} 过滤是必须的，不是优化。</b> 此前本类没有覆写本方法，
     * 服务端对字典项一视同仁，字典管理页只好一次性拉 {@code size=500} 再在浏览器里按类型筛——
     * 那是把分页接口当全量接口使，全库字典项超过 500 条就开始静默丢数据，
     * 且症状（某个类型下少了几项）完全不指向真因。同类问题的完整约定见 CLAUDE.md 4.11。
     *
     * <p>{@code dictType} 用精确匹配而非模糊：它是字典项的归属键，不是搜索词。
     * {@code dictLabel} 才是给人搜的，右模糊与其余模块一致。
     *
     * <p>{@code id} 是二级排序，不能省——{@code sort} 允许重复，只按它排时同值行的
     * 相对顺序由数据库自行决定，翻页会重复或漏掉记录（同 {@code SysRoleController}）。
     */
    @Override
    protected Wrapper<SysDictData> buildListWrapper(Map<String, String> params) {
        QueryWrapper<SysDictData> wrapper = new QueryWrapper<>();
        wrapper.eq(text(params, "dictType") != null, "dict_type", text(params, "dictType"));
        wrapper.likeRight(text(params, "dictLabel") != null, "dict_label", text(params, "dictLabel"));
        wrapper.orderByAsc("sort", "id");
        return wrapper;
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
