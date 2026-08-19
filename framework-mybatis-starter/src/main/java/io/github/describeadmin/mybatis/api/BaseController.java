package io.github.describeadmin.mybatis.api;

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

    @GetMapping
    public Result<PageResult<T>> list(PageQuery query) {
        return Result.ok(getService().page(query));
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
