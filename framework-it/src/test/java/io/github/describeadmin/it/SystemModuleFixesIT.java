package io.github.describeadmin.it;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.entity.SysConfig;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.entity.SysDictType;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.service.SysConfigService;
import io.github.describeadmin.system.service.SysDeptService;
import io.github.describeadmin.system.service.SysDictTypeService;
import io.github.describeadmin.system.service.SysMenuService;
import io.github.describeadmin.system.service.SysRoleService;
import io.github.describeadmin.system.service.SysUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 0.2.2 代码审查修复的回归门禁。
 *
 * <p>这三组缺陷的共同点是<b>症状不指向真因</b>——越权的是 {@code hasAuthority} 而
 * {@code hasRole} 正常、字段清不掉但界面看起来清了、400 的输入问题以 500 收场。
 * 光看页面或光看日志都判断不出来，只能在真实数据库上按具体值断言（CLAUDE.md §3.6）。
 */
@DisplayName("0.2.2 审查修复回归")
class SystemModuleFixesIT extends AbstractMySqlIntegrationTest {

    @Autowired SysUserService userService;
    @Autowired SysDeptService deptService;
    @Autowired SysRoleService roleService;
    @Autowired SysMenuService menuService;
    @Autowired SysDictTypeService dictTypeService;
    @Autowired SysConfigService configService;
    @Autowired SysRelationMapper relationMapper;

    // ----------------------------------------------------------- 逻辑删除的角色

    @Nested
    @DisplayName("逻辑删除的角色不再授权")
    class DeletedRole {

        @Test
        @DisplayName("删除角色后，权限点 / 菜单 / 角色标识三个口径同步失效")
        void deletedRoleGrantsNothing() {
            SysMenu menu = new SysMenu();
            menu.setParentId(0L);
            menu.setMenuName("回归用菜单");
            menu.setMenuType(SysMenu.TYPE_MENU);
            menu.setPermCode("regress:deleted-role:list");
            menu.setPath("/regress/deleted-role");
            menu.setComponent("regress/deleted-role/index");
            menu.setSort(999);
            menu.setVisible(1);
            menuService.save(menu);

            SysRole role = new SysRole();
            role.setRoleCode("REGRESS_DELETED");
            role.setRoleName("待删除角色");
            role.setSort(999);
            roleService.save(role);
            roleService.assignMenus(role.getId(), List.of(menu.getId()));

            SysUser user = new SysUser();
            user.setUsername("regress-deleted-role");
            user.setNickname("角色删除回归");
            SysUser created = userService.createUser(user, "P@ssw0rd-123", List.of(role.getId()));
            Long userId = created.getId();

            // 前置：删除之前三个口径都拿得到，否则后面的"空"说明不了任何问题
            assertThat(relationMapper.selectPermCodesByUserId(userId))
                    .as("删除前应持有该权限点")
                    .contains("regress:deleted-role:list");
            assertThat(relationMapper.selectMenuIdsByUserId(userId)).contains(menu.getId());
            assertThat(relationMapper.selectRoleCodesByUserId(userId)).contains("REGRESS_DELETED");

            roleService.removeById(role.getId());

            // 删除角色是 sys_role 的逻辑删除，sys_user_role 的关联行仍在（授权是重建语义）。
            // 关联查询若只过滤 sys_menu.deleted，被删角色会继续下发权限点与菜单路由。
            assertThat(relationMapper.selectRoleCodesByUserId(userId))
                    .as("角色标识：本就过滤了 r.deleted，删除后必须为空")
                    .isEmpty();
            assertThat(relationMapper.selectPermCodesByUserId(userId))
                    .as("权限点：与角色标识同口径，否则 hasRole 失效而 hasAuthority 照过")
                    .isEmpty();
            assertThat(relationMapper.selectMenuIdsByUserId(userId))
                    .as("菜单 id：treeOf 唯一的访问控制依据，漏过滤等于路由照常下发")
                    .isEmpty();
            assertThat(menuService.treeOf(userId))
                    .as("前端路由树也必须为空")
                    .isEmpty();
        }
    }

    // ----------------------------------------------------------- 菜单可空列清空

    @Nested
    @DisplayName("菜单可空列能清空回 NULL")
    class NullableColumns {

        @Test
        @DisplayName("5 个可空列传空串提交后全部落成 NULL")
        void allFiveNullableColumnsCanBeCleared() {
            SysMenu menu = new SysMenu();
            menu.setParentId(0L);
            menu.setMenuName("可空列回归");
            menu.setMenuType(SysMenu.TYPE_MENU);
            menu.setPermCode("regress:nullable:list");
            menu.setPath("/regress/nullable");
            menu.setComponent("regress/nullable/index");
            menu.setIcon("lucide:eraser");
            menu.setActivePath("/regress");
            menu.setSort(998);
            menu.setVisible(1);
            menuService.save(menu);

            SysMenu saved = menuService.getById(menu.getId());
            assertThat(saved.getPath()).isEqualTo("/regress/nullable");
            assertThat(saved.getComponent()).isEqualTo("regress/nullable/index");

            // 前端表单清空后提交的是空串，不是 null——这正是 path/component 曾经清不掉的原因
            SysMenu update = new SysMenu();
            update.setId(saved.getId());
            update.setVersion(saved.getVersion());
            update.setPermCode("");
            update.setPath("");
            update.setComponent("");
            update.setIcon("");
            update.setActivePath("");
            assertThat(menuService.updateById(update)).isTrue();

            SysMenu cleared = menuService.getById(saved.getId());
            assertThat(cleared.getPermCode()).as("perm_code").isNull();
            assertThat(cleared.getPath()).as("path：空串走不到 null 分支时这里会是 ''").isNull();
            assertThat(cleared.getComponent()).as("component：同 path").isNull();
            assertThat(cleared.getIcon()).as("icon").isNull();
            assertThat(cleared.getActivePath()).as("active_path").isNull();
            assertThat(cleared.getMenuName())
                    .as("未传的字段不受影响（NOT_NULL 更新策略照常跳过）")
                    .isEqualTo("可空列回归");
        }

        /**
         * 固化 {@code updateById} 的真实契约：这 5 个可空列<b>不支持局部更新</b>。
         *
         * <p>{@code clearColumnIfNull} 对每个值为 {@code null} 的列补一条 {@code col = NULL}，
         * 所以"没传"与"传空串"在这 5 列上是同一件事——调用方必须每次带全 5 列。
         * 这是 {@code updateById} javadoc 写明的取舍（要么实体赋值、要么 wrapper 赋值，
         * 每列恰好一次），代价是它与其余列的 {@code NOT_NULL} 语义不同，很容易误判为 bug。
         */
        @Test
        @DisplayName("可空列不支持局部更新：没传 = 清空；非可空列仍按 NOT_NULL 保留")
        void nullableColumnsAreAllOrNothing() {
            SysMenu menu = new SysMenu();
            menu.setParentId(0L);
            menu.setMenuName("部分清空回归");
            menu.setMenuType(SysMenu.TYPE_MENU);
            menu.setPermCode("regress:partial:list");
            menu.setPath("/regress/partial");
            menu.setComponent("regress/partial/index");
            menu.setIcon("lucide:pin");
            menu.setSort(997);
            menu.setVisible(1);
            menuService.save(menu);

            SysMenu saved = menuService.getById(menu.getId());
            SysMenu update = new SysMenu();
            update.setId(saved.getId());
            update.setVersion(saved.getVersion());
            update.setPath("/regress/partial-v2");
            assertThat(menuService.updateById(update)).isTrue();

            SysMenu after = menuService.getById(saved.getId());
            assertThat(after.getPath()).as("显式改值").isEqualTo("/regress/partial-v2");
            assertThat(after.getComponent())
                    .as("可空列没传 = 清空（wrapper 补 col = NULL），不是保留")
                    .isNull();
            assertThat(after.getIcon()).isNull();
            assertThat(after.getPermCode()).isNull();
            assertThat(after.getMenuName())
                    .as("非可空列不在 clearColumnIfNull 名单里，仍按 NOT_NULL 策略跳过")
                    .isEqualTo("部分清空回归");
            assertThat(after.getSort()).isEqualTo(997);
        }
    }

    // ----------------------------------------------------------- 空标识以 400 收场

    @Nested
    @DisplayName("用户列表带出部门名称")
    class UserListDeptName {

        @Test
        @DisplayName("列表返回的 deptName 是部门的真实名称，未设部门的留 null")
        void listCarriesDeptName() {
            SysDept dept = new SysDept();
            dept.setParentId(0L);
            dept.setDeptName("部门名回归部");
            dept.setSort(998);
            dept.setStatus(1);
            Long deptId = deptService.createDept(dept).getId();

            SysUser withDept = new SysUser();
            withDept.setUsername("regress-deptname-yes");
            withDept.setNickname("有部门");
            withDept.setDeptId(deptId);
            userService.createUser(withDept, "P@ssw0rd-123", List.of());

            SysUser withoutDept = new SysUser();
            withoutDept.setUsername("regress-deptname-no");
            withoutDept.setNickname("无部门");
            userService.createUser(withoutDept, "P@ssw0rd-123", List.of());

            PageQuery query = new PageQuery();
            query.setCurrent(1);
            query.setSize(500);
            PageResult<SysUser> page = userService.page(query);

            // 按具体值断言：deptName 是非持久化字段，只比对行数看不出它恒为 null（CLAUDE.md §3.6）
            assertThat(page.getRecords())
                    .filteredOn(u -> "regress-deptname-yes".equals(u.getUsername()))
                    .singleElement()
                    .extracting(SysUser::getDeptName)
                    .isEqualTo("部门名回归部");
            assertThat(page.getRecords())
                    .filteredOn(u -> "regress-deptname-no".equals(u.getUsername()))
                    .singleElement()
                    .extracting(SysUser::getDeptName)
                    .as("未设部门的用户不该被填成别人的部门")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("唯一标识为空返回 400 而不是 500")
    class BlankUniqueKey {

        @Test
        @DisplayName("新建：用户名 / 角色标识 / 字典类型 / 参数键为空或空白一律 400")
        void blankKeyOnSaveIsRejected() {
            // 不校验的话这一路全程静默：UniqueGuard 对空值跳过加锁、
            // eq(col, null) 生成恒不成立的 col = NULL、最后由 DB 的 NOT NULL 以 500 收场
            assertBadRequest(() -> userService.createUser(newUser(null), "P@ssw0rd-123", List.of()));
            assertBadRequest(() -> userService.createUser(newUser("   "), "P@ssw0rd-123", List.of()));

            assertBadRequest(() -> roleService.save(newRole(null)));
            assertBadRequest(() -> roleService.save(newRole("   ")));

            assertBadRequest(() -> dictTypeService.save(newDictType(null)));
            assertBadRequest(() -> dictTypeService.save(newDictType("   ")));

            assertBadRequest(() -> configService.save(newConfig(null)));
            assertBadRequest(() -> configService.save(newConfig("   ")));
        }

        @Test
        @DisplayName("编辑：空白非 null 是显式清空，同样 400（空串绕得过 DB 的 NOT NULL）")
        void blankKeyOnUpdateIsRejected() {
            SysRole role = newRole("REGRESS_BLANK_UPDATE");
            roleService.save(role);
            SysRole roleUpdate = new SysRole();
            roleUpdate.setId(role.getId());
            roleUpdate.setRoleCode("");
            assertBadRequest(() -> roleService.updateById(roleUpdate));

            SysDictType dict = newDictType("regress_blank_update");
            dictTypeService.save(dict);
            SysDictType dictUpdate = new SysDictType();
            dictUpdate.setId(dict.getId());
            dictUpdate.setDictType("  ");
            assertBadRequest(() -> dictTypeService.updateById(dictUpdate));

            SysConfig config = newConfig("regress.blank.update");
            configService.save(config);
            SysConfig configUpdate = new SysConfig();
            configUpdate.setId(config.getId());
            configUpdate.setConfigKey("");
            assertBadRequest(() -> configService.updateById(configUpdate));
        }

        @Test
        @DisplayName("编辑：标识传 null 仍表示「不改这个字段」，不受新校验影响")
        void nullKeyOnUpdateStillMeansUnchanged() {
            SysRole role = newRole("REGRESS_NULL_KEEPS");
            role.setRoleName("原名");
            roleService.save(role);

            SysRole saved = roleService.getById(role.getId());
            SysRole update = new SysRole();
            update.setId(saved.getId());
            update.setVersion(saved.getVersion());
            update.setRoleName("改过的名字");
            assertThat(roleService.updateById(update)).isTrue();

            SysRole after = roleService.getById(saved.getId());
            assertThat(after.getRoleName()).isEqualTo("改过的名字");
            assertThat(after.getRoleCode())
                    .as("没传 roleCode = 不改，必须原样保留")
                    .isEqualTo("REGRESS_NULL_KEEPS");
        }

        private void assertBadRequest(Runnable action) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getCode())
                    .as("必须是 400；落到 DB 的 NOT NULL 就成了 500，报错里没有东西指向「标识没填」")
                    .isEqualTo(ResultCode.BAD_REQUEST.getCode());
        }

        private SysUser newUser(String username) {
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setNickname("空标识回归");
            return user;
        }

        private SysRole newRole(String roleCode) {
            SysRole role = new SysRole();
            role.setRoleCode(roleCode);
            role.setRoleName("空标识回归");
            role.setSort(996);
            return role;
        }

        private SysDictType newDictType(String dictType) {
            SysDictType dict = new SysDictType();
            dict.setDictType(dictType);
            dict.setDictName("空标识回归");
            dict.setStatus(1);
            return dict;
        }

        private SysConfig newConfig(String configKey) {
            SysConfig config = new SysConfig();
            config.setConfigKey(configKey);
            config.setConfigValue("v");
            config.setConfigName("空标识回归");
            return config;
        }
    }
}
