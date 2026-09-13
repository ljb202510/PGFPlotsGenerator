package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.dto.notice.NoticeRowVO;
import com.pg.pgfplots.entity.Notice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 通知表 Mapper */
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 用户可见通知列表（广播 + 定向，含已读标记） */
    List<NoticeRowVO> selectUserNotices(@Param("userId") Integer userId);

    /** 用户可见通知的未读统计（total / unread） */
    java.util.Map<String, Object> countUnread(@Param("userId") Integer userId);
}
