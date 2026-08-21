package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.security.api.AuthUser;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.RoleScope;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.service.SysUserService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 {@code sys_*} 表的默认 {@link AuthUserLoader} 实现。
 *
 * <p>业务方引入 framework-system-starter 即自动获得完整的本地用户体系，
 * 无需自己实现这个 SPI。
 *
 * <p>业主已有统一认证体系（浙政钉等）时，有两种做法，互不冲突：
 * <ul>
 *   <li>只换<b>认证方式</b>：新增一个 {@code AuthProvider} 插件，本类继续负责授权数据</li>
 *   <li>连<b>用户数据</b>也在外部：自行提供 {@code AuthUserLoader} Bean 覆盖本实现</li>
 * </ul>
 */
public class DbAuthUserLoader implements AuthUserLoader {

    private final SysUserService userService;
    private final SysRelationMapper relationMapper;

    public DbAuthUserLoader(SysUserService userService, SysRelationMapper relationMapper) {
        this.userService = userService;
        this.relationMapper = relationMapper;
    }

    @Override
    public Optional<AuthUser> loadByUsername(String username) {
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            return Optional.empty();
        }

        List<RoleScope> roleScopes = relationMapper.selectDataScopesByUserId(user.getId());
        DataScopeType dataScope = DataScopeResolver.resolveType(roleScopes);
        Set<Long> customDeptIds = dataScope == DataScopeType.CUSTOM
                ? customDeptIdsOf(roleScopes)
                : Set.of();
        String homePath = HomePathResolver.resolve(roleScopes);

        return Optional.of(new AuthUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getNickname(),
                user.getStatus() != null && user.getStatus() == 1,
                new LinkedHashSet<>(relationMapper.selectRoleCodesByUserId(user.getId())),
                new LinkedHashSet<>(relationMapper.selectPermCodesByUserId(user.getId())),
                user.getDeptId(),
                dataScope,
                customDeptIds,
                homePath));
    }

    /** 合并结果为 CUSTOM 时，并集全部同样标了 CUSTOM 档的角色各自配置的部门。 */
    private Set<Long> customDeptIdsOf(List<RoleScope> roleScopes) {
        Set<Long> result = new LinkedHashSet<>();
        for (RoleScope roleScope : roleScopes) {
            if (roleScope.getDataScope() != null
                    && DataScopeType.ofCode(roleScope.getDataScope()) == DataScopeType.CUSTOM) {
                result.addAll(relationMapper.selectDeptIdsByRoleId(roleScope.getRoleId()));
            }
        }
        return result;
    }
}
