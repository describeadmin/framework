package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Controller 基类，提供标准 CRUD 端点。
 *
 * <p>业务 Controller 继承本类后即拥有 list / get / create / update / delete 五个端点，
 * 只需补充业务特有接口。所有方法均返回 {@link Result}，异常交给全局异常处理器。
 *
 * <p>子类需标注 {@code @RestController} 与 {@code @RequestMapping("/api/xxx")}。
 * 需要收窄能力时覆写对应方法并加 {@code @Override}，或直接不继承本类。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 *
 * @param <S> Service 类型
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 */
public abstract class BaseController<S extends BaseService<M, T>,
        M extends BaseMapper<T>, T extends BaseEntity> {

    /**
     * @return 业务 Service 实例，由子类提供（通常构造注入）
     */
    protected abstract S getService();

    /**
     * 分页列表。
     *
     * <p>筛选条件由 {@link #buildListWrapper(Map)} 决定，默认不筛选。
     *
     * <p><b>为什么把原始参数整体接进来，而不是让子类各自声明 {@code @RequestParam}</b>：
     * 子类若声明一个签名不同的 {@code list(...)} 并标 {@code @GetMapping}，
     * 它<b>不是覆写而是重载</b>，于是同一个 {@code GET} 路径上出现两个映射，
     * Spring 启动时直接报 Ambiguous mapping。留一个签名固定的入口 + 一个覆写点，
     * 从结构上杜绝这种写法（本项目实测踩过）。
     */
    @GetMapping
    public Result<PageResult<T>> list(PageQuery query,
                                      @RequestParam(required = false) Map<String, String> params) {
        return Result.ok(getService().page(query, buildListWrapper(
                params == null ? Map.of() : params)));
    }

    /**
     * 构造列表查询的筛选条件。默认不筛选。
     *
     * <p>子类覆写本方法即可支持条件查询；codegen 会按 spec 里的 {@code query} 字段
     * 自动生成实现。参数是原始查询串（含 {@code current}/{@code size}，忽略即可），
     * 类型转换由子类负责——框架不知道每个字段该转成什么。
     *
     * @param params 请求的全部查询参数，不为 null
     * @return 筛选条件；返回 {@code null} 表示不筛选
     */
    protected Wrapper<T> buildListWrapper(Map<String, String> params) {
        return null;
    }

    @GetMapping("/{id}")
    public Result<T> get(@PathVariable Long id) {
        T entity = getService().getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在: " + id);
        }
        return Result.ok(entity);
    }

    @PostMapping
    public Result<T> create(@RequestBody T entity) {
        // 主键由数据库自增或全局 ID 策略生成，忽略客户端传入值，防止越权指定主键
        entity.setId(null);
        getService().save(entity);
        return Result.ok(entity);
    }

    @PutMapping("/{id}")
    public Result<T> update(@PathVariable Long id, @RequestBody T entity) {
        // 以路径上的 id 为准，避免 body 与 path 不一致导致改错记录
        entity.setId(id);
        if (!getService().updateById(entity)) {
            // updateById 返回 false 的两种可能：记录不存在，或乐观锁版本冲突
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在或已被他人修改: " + id);
        }
        return Result.ok(getService().getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // BaseEntity 上有 @TableLogic，这里执行的是逻辑删除
        if (!getService().removeById(id)) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在: " + id);
        }
        return Result.ok();
    }
}
