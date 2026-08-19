package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;

/**
 * Service 基类。
 *
 * <p>业务 Service 继承本类即可获得完整的 CRUD 与分页能力，只需填充业务特有方法——
 * 这正是「薄业务代码 + 厚框架基类」的落点：框架升级时改的是本类，业务方的薄代码不必跟着改。
 *
 * <p><b>⚠️ 包路径注意</b>：MyBatis-Plus 3.5.17 中 {@code ServiceImpl} 位于
 * {@code com.baomidou.mybatisplus.spring.service.impl}（制品 {@code mybatis-plus-spring}），
 * <b>不是</b>网上绝大多数资料和 AI 生成代码里的
 * {@code com.baomidou.mybatisplus.extension.service.impl}。详见 CLAUDE.md。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 */
public abstract class BaseService<M extends BaseMapper<T>, T extends BaseEntity>
        extends ServiceImpl<M, T> {

    /**
     * 分页查询，返回框架统一的 {@link PageResult}。
     *
     * <p>不向外返回 MyBatis-Plus 的 {@code IPage}，避免前端契约与 ORM 实现绑死。
     */
    public PageResult<T> page(PageQuery query, Wrapper<T> wrapper) {
        Page<T> page = Page.of(query.getCurrent(), query.getSize());
        Page<T> result = getBaseMapper().selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /** 无条件分页查询。 */
    public PageResult<T> page(PageQuery query) {
        return page(query, null);
    }
}
