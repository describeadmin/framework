package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
     * <p>目录、普通菜单可以没有权限标识，图标也可留空。前端表单清空后提交的是空串
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

    @Override
    public boolean updateById(SysMenu entity) {
        normalizeBlankToNull(entity);
        return super.updateById(entity);
    }

    private static void normalizeBlankToNull(SysMenu menu) {
        if (menu.getPermCode() != null && menu.getPermCode().isBlank()) {
            menu.setPermCode(null);
        }
        if (menu.getIcon() != null && menu.getIcon().isBlank()) {
            menu.setIcon(null);
        }
    }

    /** 全量菜单树（管理端用）。 */
    public List<SysMenu> tree() {
        return toTree(list(new QueryWrapper<SysMenu>().orderByAsc("sort")));
    }

    /**
     * 指定用户可见的菜单树（前端路由用）。
     *
     * <p>只返回目录与菜单，不含按钮——按钮属于权限点，随 {@code /api/auth/me}
     * 的 permissions 字段下发，前端据此控制按钮显隐。
     */
    public List<SysMenu> treeOf(Long userId) {
        Set<Long> allowed = Set.copyOf(relationMapper.selectMenuIdsByUserId(userId));
        if (allowed.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenu> visible = list(new QueryWrapper<SysMenu>()
                .in("id", allowed)
                .ne("menu_type", SysMenu.TYPE_BUTTON)
                .eq("visible", 1)
                .orderByAsc("sort"));
        return toTree(visible);
    }

    private List<SysMenu> toTree(List<SysMenu> flat) {
        return TreeBuilder.build(flat, SysMenu::getId, SysMenu::getParentId, SysMenu::getChildren);
    }
}
