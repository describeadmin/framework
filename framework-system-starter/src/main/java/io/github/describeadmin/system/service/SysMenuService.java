package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.common.api.TreeBuilder;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.mapper.SysMenuMapper;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 菜单管理。 */
@Service
public class SysMenuService extends BaseService<SysMenuMapper, SysMenu> {

    private final SysRelationMapper relationMapper;

    public SysMenuService(SysRelationMapper relationMapper) {
        this.relationMapper = relationMapper;
    }

    /**
     * 落库前把空白 {@code permCode}/{@code icon} 归一为 {@code null}。
     *
     * <p>目录、普通菜单可以没有权限标识，图标与高亮路径也可留空。前端表单清空后提交的是空串
     * 而非 {@code null}，直接写库就得到 {@code ''}——与"未填 = NULL"的语义冲突，
     * 且空串权限标识会污染用户权限集合。这里在唯一的写入口统一处理，是治本；
     * {@code SysRelationMapper.selectPermCodesByUserId} 的过滤与
     * {@code TokenAuthenticationFilter} 的容错是对既有脏数据的兜底。
     */
    @Override
    public boolean save(SysMenu entity) {
        normalizeBlankToNull(entity);
        return super.save(entity);
    }

    /**
     * 编辑菜单。相比默认实现，额外保证几个可空列能被"清空回 {@code NULL}"。
     *
     * <p>MyBatis-Plus 全局 {@code updateStrategy} 默认 {@code NOT_NULL}，{@code updateById}
     * 生成的 UPDATE 会跳过所有 {@code null} 字段——于是 {@code perm_code} / {@code path} /
     * {@code component} / {@code icon} / {@code active_path} 一旦有值就再也清不掉（表单删空、
     * 提交，界面看起来清了，刷新后旧值又回来）。
     *
     * <p>{@code update(entity, wrapper)} 的 SET 子句 = 实体的非 {@code null} 字段（{@code NOT_NULL}
     * 策略）+ wrapper 的 {@code .set(...)}。于是每个可空列分两种情况：
     * <ul>
     *   <li>值非 {@code null}：实体那条路已经生成 {@code col = #{et.col}}，wrapper 不再管——
     *       否则同列被赋值两次，生成 {@code SET col=#{et.col}, ..., col=?}，达梦 / 金仓等
     *       国产化库可能直接拒绝（CLAUDE.md §3）；</li>
     *   <li>值为 {@code null}：实体跳过它（bug 根源），由 wrapper 补一条 {@code col = NULL}。</li>
     * </ul>
     * 两条路合起来，这 5 个列每次恰好被赋值一次，且能真正写回 {@code NULL}。其余字段、
     * 乐观锁（{@code @Version}）、审计填充（{@code update_time} / {@code update_by}）、
     * {@code @TableLogic} 仍由实体参数驱动，照旧。
     *
     * <p>不改全局 / 字段更新策略是有意的：那是全局开关，牵动所有实体（对照 {@code SysUser}
     * 的逐字段 {@code @TableField} 取舍）。
     */
    @Override
    public boolean updateById(SysMenu entity) {
        normalizeBlankToNull(entity);

        UpdateWrapper<SysMenu> wrapper = new UpdateWrapper<>();
        // update(entity, wrapper) 的 WHERE 取自 wrapper，不会自动用实体 id
        wrapper.eq("id", entity.getId());
        clearColumnIfNull(wrapper, "perm_code", entity.getPermCode());
        clearColumnIfNull(wrapper, "path", entity.getPath());
        clearColumnIfNull(wrapper, "component", entity.getComponent());
        clearColumnIfNull(wrapper, "icon", entity.getIcon());
        clearColumnIfNull(wrapper, "active_path", entity.getActivePath());

        // ServiceImpl.update(T, Wrapper) -> baseMapper.update(et, ew)，不回调本类的 updateById
        return update(entity, wrapper);
    }

    /**
     * 字段被清空（{@code null}）时往 wrapper 补一条 {@code col = NULL}；非 {@code null} 时
     * 不管——那条由实体参数负责，重复 set 同一列会在部分国产化库上报错。
     */
    private static void clearColumnIfNull(UpdateWrapper<SysMenu> wrapper, String column, String value) {
        if (value == null) {
            wrapper.set(column, null);
        }
    }

    private static void normalizeBlankToNull(SysMenu menu) {
        if (menu.getPermCode() != null && menu.getPermCode().isBlank()) {
            menu.setPermCode(null);
        }
        if (menu.getIcon() != null && menu.getIcon().isBlank()) {
            menu.setIcon(null);
        }
        if (menu.getActivePath() != null && menu.getActivePath().isBlank()) {
            menu.setActivePath(null);
        }
    }

    /** 全量菜单树（管理端用）。 */
    public List<SysMenu> tree() {
        return toTree(list(new QueryWrapper<SysMenu>().orderByAsc("sort")));
    }

    /**
     * 指定用户有权访问的菜单树（前端路由用）。
     *
     * <p>只返回目录与菜单，不含按钮——按钮属于权限点，随 {@code /api/auth/me}
     * 的 permissions 字段下发，前端据此控制按钮显隐。
     *
     * <p><b>刻意不按 {@code visible} 过滤</b>：显隐与授权是两件事。这里过滤掉
     * {@code visible = 0} 的话，记录根本不下发、前端也就不会生成路由，直接敲 URL 落 404——
     * 那是「停用」而非「隐藏」，业务方就没有任何办法做出「页面存在但不进侧边栏」的
     * 独立新增/编辑页。访问控制由上面的 {@code selectMenuIdsByUserId} 授权关系独家把关，
     * {@code visible} 原样下发给前端转成路由的 {@code meta.hideInMenu}。
     */
    public List<SysMenu> treeOf(Long userId) {
        Set<Long> allowed = Set.copyOf(relationMapper.selectMenuIdsByUserId(userId));
        if (allowed.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenu> routable = list(new QueryWrapper<SysMenu>()
                .in("id", allowed)
                .ne("menu_type", SysMenu.TYPE_BUTTON)
                .orderByAsc("sort"));
        return toTree(routable);
    }

    private List<SysMenu> toTree(List<SysMenu> flat) {
        return TreeBuilder.build(flat, SysMenu::getId, SysMenu::getParentId, SysMenu::getChildren);
    }
}
