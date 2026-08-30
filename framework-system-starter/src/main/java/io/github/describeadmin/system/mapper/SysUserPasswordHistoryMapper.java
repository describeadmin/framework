package io.github.describeadmin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.system.entity.SysUserPasswordHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户历史密码 Mapper。写入用继承自 {@link BaseMapper} 的 {@code insert}。
 *
 * <p>只在 {@code sys.password.history-count > 0} 时被 {@code SysUserService} 调用做重用校验。
 */
public interface SysUserPasswordHistoryMapper extends BaseMapper<SysUserPasswordHistory> {

    /** 取某用户最近 {@code limit} 条历史密码哈希，新的在前。 */
    @Select("SELECT password_hash FROM sys_user_password_history "
            + "WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit}")
    List<String> selectRecentHashes(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 裁剪：只保留某用户最近 {@code keep} 条历史，更早的物理删除。
     *
     * <p>5.7-safe：不用窗口函数 / CTE。DELETE 不能直接引用子查询里的同名表，
     * 因此套一层派生表 {@code t}。
     */
    @Delete("DELETE FROM sys_user_password_history WHERE user_id = #{userId} AND id NOT IN ("
            + "SELECT id FROM (SELECT id FROM sys_user_password_history "
            + "WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{keep}) t)")
    int pruneToRecent(@Param("userId") Long userId, @Param("keep") int keep);
}
