package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.core.TreeBuilder;
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

    /** 全量菜单树（管理端用）。 */
    public List<SysMenu> tree() {
        return toTree(list(new QueryWrapper<SysMenu>().orderByAsc("sort")));
    }

    /**
     * 指定用户可见的菜单树（前端路由用）。
     *
     * <p>只返回目录与菜单，不含按钮——按钮属于权限点，前端通过
     * {@code /api/auth/permissions} 单独获取，用于控制按钮显隐。
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
