package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.entity.NoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 通知阅读记录表 Mapper */
public interface NoticeReadMapper extends BaseMapper<NoticeRead> {

    /** 标记单条通知已读（INSERT IGNORE 幂等） */
    @Insert("INSERT IGNORE INTO notice_read (user_id, notice_id, read_time) VALUES (#{userId}, #{noticeId}, NOW())")
    int insertIgnore(@Param("userId") Integer userId, @Param("noticeId") Integer noticeId);

    /** 标记所有可见通知已读（INSERT IGNORE 幂等） */
    @Insert("INSERT IGNORE INTO notice_read (user_id, notice_id, read_time) "
            + "SELECT #{userId}, notice_id, NOW() FROM notice "
            + "WHERE target_user_id IS NULL OR target_user_id = #{userId}")
    int insertIgnoreAll(@Param("userId") Integer userId);
}
