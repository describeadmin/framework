package io.github.describeadmin.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SysUser Mapper。 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按手机号查询，忽略数据权限过滤。
     *
     * <p>"这个手机号是否已被占用" 是全局唯一性问题，不是"我能看到哪些人"的问题——
     * 按 {@link #selectSelfById(Long)} 同样的理由排除数据权限，否则一个 SELF/DEPT 档的
     * 操作者（自助改资料的普通用户、或部门范围受限的管理员建号）会因为看不到"别的部门/
     * 别人创建"的记录，而把明明已被占用的手机号误判为可用。登录场景（认证前，未登录，
     * 无数据权限上下文）本来就不受这个过滤影响，这里统一处理不改变那条路径的行为。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM sys_user WHERE mobile = #{mobile} AND deleted = 0 LIMIT 1")
    SysUser selectByMobileIgnoreDataScope(@Param("mobile") String mobile);

    /** 按邮箱查询，忽略数据权限过滤，原因同 {@link #selectByMobileIgnoreDataScope(String)}。 */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM sys_user WHERE email = #{email} AND deleted = 0 LIMIT 1")
    SysUser selectByEmailIgnoreDataScope(@Param("email") String email);

    /**
     * 自助场景专用：按 id 查询，忽略数据权限过滤。
     *
     * <p>{@code framework-mybatis-starter} 的 {@code DeptDataPermissionHandler} 给
     * {@code sys_user} 表按登录态注入了行级过滤；{@code SELF} 档（无角色用户的默认落点）
     * 的过滤条件是 {@code create_by = 当前用户id}，而用户自己的账号几乎总是别人
     * （管理员）创建的——结果是"自己查自己都查不到"。"我能不能看/改我自己的账号"
     * 与"我能看哪些人的数据"是两个不同的问题，前者不该受后者的过滤条件约束，
     * 因此这里用 MyBatis-Plus 官方提供的 {@code @InterceptorIgnore} 显式绕开。
     *
     * <p><b>调用方必须自行保证传入的 id 就是当前登录用户自己的 id</b>——本方法
     * 不做任何权限校验，用来查询"别人"的 id 就是越权读取。仅供
     * {@code SysUserService} 的自助方法内部使用，不要在别处直接调用。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0")
    SysUser selectSelfById(@Param("id") Long id);

    /**
     * 自助改密专用：只更新 password 列，同样忽略数据权限过滤，原因见
     * {@link #selectSelfById(Long)}。数据权限拦截器同时覆盖 UPDATE，
     * 不单独处理这一条，写入会和查询一样被过滤掉、静默影响 0 行。
     *
     * <p>手写 SQL 绕过了 {@code MetaObjectHandler} 的自动填充与 {@code @Version}
     * 乐观锁的自动递增，因此这里手动维护 {@code update_by}/{@code update_time}/
     * {@code version}——版本号必须照样 +1，否则后续管理员侧基于旧版本号的编辑
     * 会把这次自助改密的结果当作"没变化"而覆盖掉。
     *
     * <p>一并把 {@code pwd_reset_required} 清 0（自助改密是解除强制改密的唯一途径）、
     * 刷新 {@code pwd_update_time}（重置「定期过期」的计时起点）。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE sys_user SET password = #{password}, pwd_reset_required = 0, pwd_update_time = NOW(), "
            + "update_by = #{id}, update_time = NOW(), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0")
    int updateSelfPassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 自助改资料专用：只更新 nickname/mobile/email 三列，同样忽略数据权限过滤，
     * 原因与手动维护审计字段/版本号见 {@link #updateSelfPassword(Long, String)}。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE sys_user SET nickname = #{nickname}, mobile = #{mobile}, email = #{email}, "
            + "update_by = #{id}, update_time = NOW(), version = version + 1 WHERE id = #{id} AND deleted = 0")
    int updateSelfProfile(@Param("id") Long id, @Param("nickname") String nickname,
                          @Param("mobile") String mobile, @Param("email") String email);
}
