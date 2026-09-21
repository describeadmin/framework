package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import io.github.describeadmin.common.api.OptionItem;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;

import java.util.List;

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

    /**
     * 取「选项」端点的候选集：不分页地返回全部匹配行，但带一个防御性上界。
     *
     * <p>选项端点（下拉框、树选择器的数据源，约定见 CLAUDE.md 4.11）必须返回<b>完整</b>集合
     * ——调用方用它渲染一个下拉框，少一项就是选不到。所以这里不接受 {@link PageQuery}：
     * 那个类型的 {@code size} 被 {@link PageQuery#MAX_SIZE} 夹在 500 以内，
     * 正是选项端点<b>不能</b>使用分页接口的原因。
     *
     * <p>上界靠多取一条实现：查 {@code MAX_SIZE + 1} 条，多出来的那条只用于判断是否超限。
     * 超限时 {@link OptionItem#requireWithinLimit} 抛错而<b>不是截断</b>——
     * 静默截断正是 {@code ?size=200} 那个 bug 的形态，症状是下拉里找不到某一项，
     * 而错误信息里没有任何东西指向"返回被裁掉了"。
     *
     * <p>用分页插件而不是拼 {@code LIMIT}：方言由 {@code DbType} 配置决定（CLAUDE.md 3.5），
     * 拼字面量会在国产化库上失效。{@code searchCount = false} 跳过那次无用的 COUNT 查询。
     *
     * @param wrapper 查询条件，建议用 {@code select(...)} 只取 id 与显示名两列
     * @param what    超限时出现在错误信息里的数据名称，如 {@code "角色"}
     */
    public List<T> listOptionSource(Wrapper<T> wrapper, String what) {
        List<T> rows = getBaseMapper()
                .selectPage(Page.of(1, OptionItem.MAX_SIZE + 1L, false), wrapper)
                .getRecords();
        return OptionItem.requireWithinLimit(rows, what);
    }
}
