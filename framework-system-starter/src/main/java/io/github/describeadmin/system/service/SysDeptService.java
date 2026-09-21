package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.OptionItem;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.common.api.TreeBuilder;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 部门管理。
 *
 * <p>维护 {@code ancestors} 物化路径——{@code SysDeptController} 覆写通用 CRUD 的
 * create/update，改走本类的 {@link #createDept}/{@link #updateDept}，
 * 因为祖先路径是派生数据，不能让调用方直接摆布。
 */
@Service
public class SysDeptService extends BaseService<SysDeptMapper, SysDept> {

    /** 部门树。 */
    public List<SysDept> tree() {
        List<SysDept> all = list(new QueryWrapper<SysDept>().orderByAsc("sort"));
        return TreeBuilder.build(all, SysDept::getId, SysDept::getParentId, SysDept::getChildren);
    }

    /**
     * 部门选项树，供别的模块（用户管理的「部门」选择器、角色管理的「分配数据权限」）使用。
     *
     * <p>与 {@link #tree()} 的区别是形状而非内容：这里只下发 id + 名称 + 禁用态，
     * 不带 {@code leader}/{@code phone}/{@code ancestors}。因为它是<b>仅要求登录</b>的
     * 引用数据端点（约定见 CLAUDE.md 4.11），下发负责人姓名与联系电话超出了"填个下拉框"
     * 该有的授权范围。
     *
     * <p><b>停用的部门标记为 {@code disabled} 而不是过滤掉。</b> 直接滤掉的话，
     * 已经在该部门下的用户一打开编辑表单，部门就显示为空——看起来像"这个人没有部门"，
     * 保存一次就真的没有了。停用的语义是"不能再选它"，不是"它不存在"。
     *
     * <p>不接受 {@code keyword}：树必须完整才能渲染，过滤掉中间层会让子节点失去挂载点。
     */
    public List<OptionItem> listOptions() {
        List<SysDept> all = listOptionSource(new QueryWrapper<SysDept>()
                .select("id", "parent_id", "dept_name", "status")
                .orderByAsc("sort", "id"), "部门");
        return toOptions(TreeBuilder.build(
                all, SysDept::getId, SysDept::getParentId, SysDept::getChildren));
    }

    private static List<OptionItem> toOptions(List<SysDept> nodes) {
        List<OptionItem> options = new ArrayList<>(nodes.size());
        for (SysDept node : nodes) {
            // status：1 启用 / 0 禁用（schema-rbac.sql）。null 按启用处理，与列的 DEFAULT 1 一致
            OptionItem option = OptionItem.of(node.getId(), node.getDeptName(),
                    node.getStatus() != null && node.getStatus() == 0);
            option.getChildren().addAll(toOptions(node.getChildren()));
            options.add(option);
        }
        return options;
    }

    /** 创建部门，按父部门推算 {@code ancestors}。 */
    @Transactional(rollbackFor = Exception.class)
    public SysDept createDept(SysDept dept) {
        dept.setId(null);
        dept.setAncestors(ancestorsOfChildUnder(dept.getParentId()));
        save(dept);
        return dept;
    }

    /**
     * 更新部门。父部门变化（移动部门）时重算自身 {@code ancestors}，
     * 并级联更新全部子孙——它们的路径里都含有本部门，本部门的路径一变，
     * 子孙的路径就都不对了。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysDept updateDept(Long id, SysDept dept) {
        SysDept existing = getById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在: " + id);
        }

        dept.setId(id);
        Long newParentId = dept.getParentId();
        boolean moved = newParentId != null && !newParentId.equals(existing.getParentId());

        if (!moved) {
            dept.setAncestors(existing.getAncestors());
            if (!updateById(dept)) {
                throw new BizException(ResultCode.NOT_FOUND, "部门不存在或已被他人修改: " + id);
            }
            return getById(id);
        }

        if (newParentId.equals(id) || isDescendantOf(id, newParentId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能把部门移动到自己或自己的子部门下");
        }

        String oldSelfPath = selfPath(existing.getAncestors(), id);
        String newAncestors = ancestorsOfChildUnder(newParentId);
        String newSelfPath = selfPath(newAncestors, id);

        dept.setAncestors(newAncestors);
        if (!updateById(dept)) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在或已被他人修改: " + id);
        }
        cascadeAncestors(id, oldSelfPath, newSelfPath);
        return getById(id);
    }

    /** 部门数据量通常在百到千级（见 TreeBuilder 的既有判断），逐条更新即可，不上批量 SQL。 */
    private void cascadeAncestors(Long deptId, String oldSelfPath, String newSelfPath) {
        List<SysDept> descendants = getBaseMapper().selectDescendants(deptId);
        for (SysDept descendant : descendants) {
            String path = descendant.getAncestors();
            if (path == null || !path.startsWith(oldSelfPath)) {
                // 理论上不会发生：selectDescendants 已按 ancestors 含 deptId 过滤
                continue;
            }
            SysDept patch = new SysDept();
            patch.setId(descendant.getId());
            patch.setAncestors(newSelfPath + path.substring(oldSelfPath.length()));
            getBaseMapper().updateById(patch);
        }
    }

    private boolean isDescendantOf(Long deptId, Long candidateId) {
        return getBaseMapper().selectDescendants(deptId).stream()
                .anyMatch(d -> Objects.equals(d.getId(), candidateId));
    }

    /** 顶级部门（parentId 为 0 或 null）的 ancestors 是空串，其余是父部门的 ancestors 追加父部门自身 id。 */
    private String ancestorsOfChildUnder(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "";
        }
        SysDept parent = getById(parentId);
        if (parent == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "父部门不存在: " + parentId);
        }
        return append(parent.getAncestors(), parentId);
    }

    /** 某部门自身的完整路径（含自身），是其全部子孙 ancestors 字段的公共前缀。 */
    private String selfPath(String ancestors, Long id) {
        return append(ancestors, id);
    }

    private String append(String ancestors, Long id) {
        return (ancestors == null || ancestors.isEmpty()) ? String.valueOf(id) : ancestors + "," + id;
    }
}
