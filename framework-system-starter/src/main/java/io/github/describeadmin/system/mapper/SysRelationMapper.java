package io.github.describeadmin.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色、角色-菜单关联表操作。
 *
 * <p>关联表不设实体与审计字段：授权是【重建】语义而非【修改】语义，
 * 每次授权直接物理删除旧关系再插入新关系，保留软删记录只会让查询变复杂。
 *
 * <p><b>SQL 遵循 CLAUDE.md 3.1 红线</b>：只用 JOIN 与 IN 子查询，
 * 不使用窗口函数、CTE 等 8.0+ 特性，保证在 MySQL 5.7 与国产化库上均可执行。
 */
public interface SysRelationMapper {

    @Select("SELECT r.role_code FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0 ORDER BY r.sort")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    // TRIM(...) <> '' 把"权限标识留空"存成的空串一并挡在权限集合之外：空串不是 NULL，
    // 仅靠 IS NOT NULL 过不掉，会一路流到前端按钮显隐和 TokenAuthenticationFilter
    // （后者对空串 authority 直接抛异常）。TRIM 与 <> '' 均属 MySQL 5.7 安全子集。
    @Select("SELECT DISTINCT m.perm_code FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "WHERE m.perm_code IS NOT NULL AND TRIM(m.perm_code) <> '' AND m.deleted = 0 "
            + "AND rm.role_id IN (SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = #{userId})")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT m.id FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "WHERE m.deleted = 0 "
            + "AND rm.role_id IN (SELECT ur.role_id FROM sys_user_role ur WHERE ur.user_id = #{userId})")
    List<Long> selectMenuIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT rm.menu_id FROM sys_role_menu rm WHERE rm.role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("<script>INSERT INTO sys_user_role (user_id, role_id) VALUES "
            + "<foreach collection='roleIds' item='rid' separator=','>(#{userId}, #{rid})</foreach></script>")
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("<script>INSERT INTO sys_role_menu (role_id, menu_id) VALUES "
            + "<foreach collection='menuIds' item='mid' separator=','>(#{roleId}, #{mid})</foreach></script>")
    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

    /**
     * 当前用户全部角色的数据权限范围与默认首页，供 {@code DataScopeResolver}/
     * {@code HomePathResolver} 合并。按 {@code sort} 升序——首页合并规则是
     * "取排序靠前且非空的第一个"，需要确定性顺序。
     */
    @Select("SELECT r.id AS role_id, r.data_scope AS data_scope, r.home_path AS home_path FROM sys_role r "
            + "JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0 ORDER BY r.sort")
    List<RoleScope> selectDataScopesByUserId(@Param("userId") Long userId);

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    int deleteRoleDepts(@Param("roleId") Long roleId);

    @Insert("<script>INSERT INTO sys_role_dept (role_id, dept_id) VALUES "
            + "<foreach collection='deptIds' item='did' separator=','>(#{roleId}, #{did})</foreach></script>")
    int insertRoleDepts(@Param("roleId") Long roleId, @Param("deptIds") List<Long> deptIds);
}
