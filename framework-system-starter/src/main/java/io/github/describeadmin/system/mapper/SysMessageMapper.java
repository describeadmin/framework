package io.github.describeadmin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.system.entity.SysMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SysMessage Mapper。
 *
 * <p>收件人维度的写操作一律手写、{@code WHERE} 一律带 {@code user_id}——"只能动自己的消息"
 * 落在 SQL 里，而不是靠调用方先查后改。
 *
 * <p>已读时间由调用方按框架 {@code Clock} 传入，不用库端 {@code NOW()}——与 {@code create_time}
 * 同一个时间来源，测试里换成固定时钟也能断言。
 */
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    @Update("UPDATE sys_message SET read_time = #{now} "
            + "WHERE id = #{id} AND user_id = #{userId} AND read_time IS NULL")
    int markRead(@Param("userId") Long userId, @Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE sys_message SET read_time = #{now} WHERE user_id = #{userId} AND read_time IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE sys_message SET read_time = #{now} "
            + "WHERE category = #{category} AND biz_key = #{bizKey} AND read_time IS NULL")
    int markReadByBiz(@Param("category") String category, @Param("bizKey") String bizKey,
                      @Param("now") LocalDateTime now);

    @Delete("DELETE FROM sys_message WHERE id = #{id} AND user_id = #{userId}")
    int deleteOwn(@Param("userId") Long userId, @Param("id") Long id);

    /** 清空已读。未读的不删——待办不该被一键清掉。 */
    @Delete("DELETE FROM sys_message WHERE user_id = #{userId} AND read_time IS NOT NULL")
    int deleteReadOwn(@Param("userId") Long userId);
}
