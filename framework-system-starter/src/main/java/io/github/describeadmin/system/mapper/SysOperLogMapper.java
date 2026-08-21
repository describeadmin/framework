package io.github.describeadmin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Delete;

/**
 * SysOperLog Mapper。
 *
 * <p>{@code SysOperLog} 不继承 {@code BaseEntity}（见该类的类注释），因此
 * {@code SysOperLogService} 不能继承本框架的 {@code BaseService}——那个泛型基类要求
 * {@code T extends BaseEntity}。MyBatis-Plus 自己的 {@code BaseMapper} 没有这条限制，
 * 分页/插入/按 id 删除都能直接用继承来的方法，只多这一条"清空"需要手写。
 */
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {

    /** 清空全部操作日志，物理删除。 */
    @Delete("DELETE FROM sys_oper_log")
    int deleteAll();
}
