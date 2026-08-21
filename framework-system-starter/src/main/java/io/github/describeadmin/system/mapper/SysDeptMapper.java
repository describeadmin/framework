package io.github.describeadmin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.system.entity.SysDept;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** SysDept Mapper。 */
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 某部门的全部子孙部门（不含自身）。
     *
     * <p>{@code FIND_IN_SET} 是 MySQL 5.7-safe 的标准函数，不是窗口函数/CTE，
     * 与 CLAUDE.md 3.1 的红线不冲突。移动部门时用于批量重算子孙的 {@code ancestors}。
     */
    @Select("SELECT * FROM sys_dept WHERE FIND_IN_SET(#{deptId}, ancestors) AND deleted = 0")
    List<SysDept> selectDescendants(@Param("deptId") Long deptId);
}
