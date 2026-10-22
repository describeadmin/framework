package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysMenuMapper;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权上限：操作者只能授出（或收回）<b>自己当前持有</b>的菜单。
 *
 * <p><b>为什么需要</b>：{@code system:user:assign-role} / {@code system:role:assign-menu}
 * 只回答"能不能分配"，不回答"能分配到多大"。没有上限时，持有分配角色权限的"用户管理员"
 * 可以给自己或一个新建的账号挂上 ADMIN，重新登录即为超管——建号时 {@code roleIds}
 * 走的也是同一条授权路径，所以只禁止"操作自己"是挡不住的。
 *
 * <p><b>判据</b>：本次变更里新增<b>与移除</b>的每一个角色（或菜单），其生效菜单都必须是操作者
 * 生效菜单的子集。移除也要受限，否则下级可以把上级的 ADMIN 角色摘掉。两边都只计未删除的菜单
 * （{@link SysRelationMapper#selectLiveMenuIdsByRoleId} 与
 * {@link SysRelationMapper#selectMenuIdsByUserId} 同口径），且实时查库，不读令牌里的权限快照。
 *
 * <p><b>后果</b>：ADMIN 自己缺了某个菜单的授权（例如业务方加菜单 changeset 时没给 ADMIN 授权），
 * 就无法再把这个菜单、或含它的角色转授给别人——先给 ADMIN 补上授权即可，见 CLAUDE.md 4.5.2。
 *
 * <p>没有当前用户时（启动期种子数据、后台任务）不做限制：那是系统自身在授权，不存在"越权"。
 */
@Service
public class GrantCeiling {

    private final SysRelationMapper relationMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final CurrentUserProvider currentUser;

    public GrantCeiling(SysRelationMapper relationMapper, SysRoleMapper roleMapper, SysMenuMapper menuMapper,
                        ObjectProvider<CurrentUserProvider> currentUser) {
        this.relationMapper = relationMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.currentUser = currentUser.getIfAvailable(() -> CurrentUserProvider.NOOP);
    }

    /** 当前操作者的用户 ID；系统上下文为 {@code null}。 */
    public Long operatorId() {
        return currentUser.currentUserId();
    }

    /**
     * 角色集合由 {@code before} 变为 {@code after} 时校验。不存在或已删除的角色不参与比对——
     * 授出它们不产生任何权限（权限查询都排除了已删除角色），摘掉它们只是清理残留。
     */
    public void checkRoleChange(Collection<Long> before, Collection<Long> after) {
        Long operator = operatorId();
        if (operator == null) {
            return;
        }
        Set<Long> held = new HashSet<>(relationMapper.selectMenuIdsByUserId(operator));
        for (Long roleId : symmetricDifference(before, after)) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                continue;
            }
            if (!held.containsAll(relationMapper.selectLiveMenuIdsByRoleId(roleId))) {
                throw new BizException(ResultCode.FORBIDDEN,
                        "无权授予或移除超出自身权限的角色: " + role.getRoleName());
            }
        }
    }

    /** 某角色的菜单集合由 {@code before} 变为 {@code after} 时校验。已删除的菜单不参与比对，理由同上。 */
    public void checkMenuChange(Collection<Long> before, Collection<Long> after) {
        Long operator = operatorId();
        if (operator == null) {
            return;
        }
        Set<Long> changed = symmetricDifference(before, after);
        if (changed.isEmpty()) {
            return;
        }
        Set<Long> held = new HashSet<>(relationMapper.selectMenuIdsByUserId(operator));
        List<SysMenu> live = menuMapper.selectList(new QueryWrapper<SysMenu>().select("id").in("id", changed));
        for (SysMenu menu : live) {
            if (!held.contains(menu.getId())) {
                throw new BizException(ResultCode.FORBIDDEN, "无权授予或收回超出自身权限的菜单");
            }
        }
    }

    private static Set<Long> symmetricDifference(Collection<Long> before, Collection<Long> after) {
        Set<Long> a = before == null ? Set.of() : new HashSet<>(before);
        Set<Long> b = after == null ? Set.of() : new HashSet<>(after);
        Set<Long> diff = new HashSet<>(a);
        diff.addAll(b);
        Set<Long> common = new HashSet<>(a);
        common.retainAll(b);
        diff.removeAll(common);
        return diff;
    }
}
