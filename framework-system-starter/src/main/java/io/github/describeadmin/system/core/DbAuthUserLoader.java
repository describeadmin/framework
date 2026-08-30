package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.security.api.AuthUser;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.RoleScope;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.service.SysConfigService;
import io.github.describeadmin.system.service.SysUserService;

import java.time.Duration;
import java.time.LocalDateTime;
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

    /** 参数键：密码有效期天数（&gt; 0 生效）。与 seed-rbac.sql 的内置参数对应。 */
    private static final String CFG_MAX_AGE_DAYS = "sys.password.max-age-days";

    private final SysUserService userService;
    private final SysRelationMapper relationMapper;
    private final SysConfigService configService;

    public DbAuthUserLoader(SysUserService userService, SysRelationMapper relationMapper,
                            SysConfigService configService) {
        this.userService = userService;
        this.relationMapper = relationMapper;
        this.configService = configService;
    }

    @Override
    public Optional<AuthUser> loadByUsername(String username) {
        SysUser user = userService.findByUsername(username);
        return user == null ? Optional.empty() : buildAuthUser(user);
    }

    @Override
    public Optional<AuthUser> loadByUserId(Long userId) {
        SysUser user = userService.getById(userId);
        return user == null ? Optional.empty() : buildAuthUser(user);
    }

    /** 由已查到的 {@link SysUser} 拼出角色/权限/数据权限/首页路径俱全的 {@link AuthUser}。 */
    private Optional<AuthUser> buildAuthUser(SysUser user) {
        List<RoleScope> roleScopes = relationMapper.selectDataScopesByUserId(user.getId());
        DataScopeType dataScope = DataScopeResolver.resolveType(roleScopes);
        Set<Long> customDeptIds = dataScope == DataScopeType.CUSTOM
                ? customDeptIdsOf(roleScopes)
                : Set.of();
        String homePath = HomePathResolver.resolve(roleScopes);

        boolean pwdResetRequired =
                (user.getPwdResetRequired() != null && user.getPwdResetRequired() == 1)
                        || passwordExpired(user);

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
                homePath,
                pwdResetRequired));
    }

    /**
     * 密码是否已过有效期。{@code sys.password.max-age-days > 0} 且
     * {@code now - pwd_update_time >= 天数} 时为 true。
     *
     * <p>不落库——每次登录都按当前参数与 {@code pwd_update_time} 现算，
     * 因此改参数、改密码都能即时反映。{@code pwd_update_time} 为 null（旧库升级）时按未过期处理。
     */
    private boolean passwordExpired(SysUser user) {
        long maxAgeDays = parseNonNegativeLong(configService.getValue(CFG_MAX_AGE_DAYS, "0"));
        LocalDateTime pwdUpdateTime = user.getPwdUpdateTime();
        if (maxAgeDays <= 0 || pwdUpdateTime == null) {
            return false;
        }
        return Duration.between(pwdUpdateTime, LocalDateTime.now()).toDays() >= maxAgeDays;
    }

    private static long parseNonNegativeLong(String value) {
        try {
            return Math.max(Long.parseLong(value.trim()), 0L);
        } catch (NumberFormatException | NullPointerException e) {
            return 0L;
        }
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
