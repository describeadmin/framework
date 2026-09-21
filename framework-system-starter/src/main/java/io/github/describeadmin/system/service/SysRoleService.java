package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.UniqueGuard;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.OptionItem;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色管理。
 *
 * <p>{@code roleCode} 唯一性在应用层校验，而非数据库唯一索引——逻辑删除下建唯一索引
 * 会导致删除后无法复用同 code 角色，理由同 {@code SysUserService.username}（见
 * schema-rbac.sql 对应注释）。<b>并发唯一性由 {@link UniqueGuard} 的键级锁保证</b>。
 *
 * <p>角色名（{@code roleName}）是展示字段，允许重名——两个角色都叫"运营"是合法的，
 * 权限授予与引用走 {@code roleCode}。这一点与用户名不同：用户名是登录凭证，必须唯一。
 */
@Service
public class SysRoleService extends BaseService<SysRoleMapper, SysRole> {

    private final SysRelationMapper relationMapper;
    private final UniqueGuard uniqueGuard;
    private final SysMenuService menuService;

    public SysRoleService(SysRelationMapper relationMapper, UniqueGuard uniqueGuard,
                          SysMenuService menuService) {
        this.relationMapper = relationMapper;
        this.uniqueGuard = uniqueGuard;
        this.menuService = menuService;
    }

    /**
     * 校验角色标识在「未删除」范围内未被其他记录占用。
     *
     * <p>{@code selfId} 语义同 {@code SysDictTypeService}：新建传 {@code null}，
     * 编辑传当前 id，排除"改别的字段但 code 没变"的误判。
     *
     * <p><b>调用方必须先保证 {@code roleCode} 非空白</b>（见 {@link #requireRoleCode}）：
     * 空值进来时 {@code eq("role_code", null)} 生成恒不成立的 {@code role_code = NULL}，
     * 本方法会静默放行。
     */
    private void assertRoleCodeAvailable(Long selfId, String roleCode) {
        SysRole exist = getOne(new QueryWrapper<SysRole>().eq("role_code", roleCode), false);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色标识已存在: " + roleCode);
        }
    }

    /**
     * 角色标识不得为空白。
     *
     * <p>不校验的话这一路全程静默：{@link UniqueGuard#execute} 对空值按设计跳过加锁，
     * {@link #assertRoleCodeAvailable} 的 {@code role_code = NULL} 恒不成立，
     * 最后由数据库的 {@code NOT NULL} 约束以 <b>500</b> 收场——而这本是一个 400 的输入问题，
     * 报错里没有任何东西指向"角色标识没填"。
     */
    private static void requireRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色标识不能为空");
        }
    }

    @Override
    public boolean save(SysRole entity) {
        requireRoleCode(entity.getRoleCode());
        return uniqueGuard.execute("sys:role:code", entity.getRoleCode(), () -> {
            assertRoleCodeAvailable(null, entity.getRoleCode());
            return super.save(entity);
        });
    }

    /**
     * 编辑角色。{@code roleCode} 为 {@code null} 表示"不改这个字段"（{@code NOT_NULL}
     * 更新策略会跳过它）；但<b>空白非 null 是显式想清空</b>，而角色标识不允许为空——
     * 空串绕得过数据库的 {@code NOT NULL}，会真的把 {@code role_code = ''} 写进库。
     */
    @Override
    public boolean updateById(SysRole entity) {
        if (entity.getRoleCode() != null) {
            requireRoleCode(entity.getRoleCode());
        }
        return uniqueGuard.execute("sys:role:code", entity.getRoleCode(), () -> {
            if (entity.getRoleCode() != null) {
                assertRoleCodeAvailable(entity.getId(), entity.getRoleCode());
            }
            return super.updateById(entity);
        });
    }

    /**
     * 重新授予菜单权限："重建"语义，并且<b>把入参闭包到全部祖先</b>
     * （见 {@link SysMenuService#withAncestors}）。
     *
     * <p>只授子节点、不授父节点是无效授权：前端按父子关系生成路由，父节点缺席时子路由
     * 没有挂载点。此前靠前端提交半选的父节点兜住，那要求<b>每一个调用方都记得这件事</b>
     * ——接口被直接调用时（脚本、集成测试、以后的开放接口）就不成立了。改为在唯一的
     * 写入口闭包，库里存的直接就是事实，查表即真相。
     *
     * <p>因此 {@link #menuIdsOf} 读回的 id 可能<b>多于</b>本次传入的 id，<b>这是设计而非缺陷</b>。
     * 由此还有一个语义后果：只授某个按钮（如 {@code system:user:add}）会连带授出其父菜单的
     * {@code system:user:list}——父菜单自己也带权限点。这一点在旧前端提交半选父节点时
     * 早已如此，变的只是"直接调接口"这条路径。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        List<Long> effective = menuService.withAncestors(menuIds);
        relationMapper.deleteRoleMenus(roleId);
        if (!effective.isEmpty()) {
            relationMapper.insertRoleMenus(roleId, effective);
        }
    }

    public List<Long> menuIdsOf(Long roleId) {
        return relationMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 重新指定自定义数据权限的部门列表，同样是"重建"语义。
     *
     * <p>只在角色的 {@code dataScope = CUSTOM} 时有意义；对其余档位的角色调用本方法
     * 不会报错，但写入的部门列表在数据权限拦截器里不会被读取（见
     * {@code DeptDataPermissionHandler}），调用方自行保证 {@code data_scope} 已经是 CUSTOM。
     *
     * <p><b>⚠️ 刻意不做 {@link #assignMenus} 那样的祖先闭包，不要"补齐对称性"。</b>
     * 两者的 id 语义根本不同：菜单 id 是树上的<b>挂载点</b>，子依赖父才能被渲染，
     * 缺了父是坏数据；而这里的部门 id 是一个<b>行过滤值</b>——{@code DeptDataPermissionHandler}
     * 的 CUSTOM 档生成的是 {@code dept_column IN (...)}，精确集合成员判定，不做任何展开
     * （同类里的 {@code DEPT_AND_CHILD} 档才用 {@code FIND_IN_SET(..., ancestors)} 往下展开）。
     * 给它补祖先等于<b>让角色多看见父部门的业务数据</b>，是越权而不是修复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignDataScopeDepts(Long roleId, List<Long> deptIds) {
        relationMapper.deleteRoleDepts(roleId);
        if (deptIds != null && !deptIds.isEmpty()) {
            relationMapper.insertRoleDepts(roleId, deptIds);
        }
    }

    public List<Long> deptIdsOf(Long roleId) {
        return relationMapper.selectDeptIdsByRoleId(roleId);
    }

    /**
     * 角色选项，供别的模块（用户管理的「角色」选择器）填下拉框。
     *
     * <p>取代过去的 {@code GET /api/system/role?current=1&size=200}：那是把分页接口
     * 当全量接口使，角色超过 200 条就静默丢数据，而且不可能靠调大参数修好
     * （{@code PageQuery.MAX_SIZE} 在入口夹紧）。约定见 CLAUDE.md 4.11。
     *
     * <p><b>{@code SysRole} 没有 {@code status} 字段</b>，所以这里不像
     * {@link SysDeptService#listOptions} 那样标注禁用态——角色只有"存在 / 被逻辑删除"两种状态，
     * 后者由 {@code @TableLogic} 自动排除。
     *
     * <p>只取 {@code id} 与 {@code role_name} 两列：选项接口的契约就是最小形状，
     * 多带 {@code data_scope}/{@code home_path} 等字段等于把角色管理的内部结构
     * 泄露给每一个登录用户。
     *
     * @param keyword 角色名前缀，可为空（返回全量）。无论传不传，超过
     *                {@code OptionItem.MAX_SIZE} 都抛错而非截断
     */
    public List<OptionItem> listOptions(String keyword) {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<SysRole>()
                .select("id", "role_name")
                .likeRight(StringUtils.hasText(keyword), "role_name", keyword)
                .orderByAsc("sort", "id");
        return listOptionSource(wrapper, "角色").stream()
                .map(r -> OptionItem.of(r.getId(), r.getRoleName()))
                .toList();
    }
}
