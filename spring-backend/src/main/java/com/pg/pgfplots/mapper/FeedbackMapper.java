package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.dto.feedback.FeedbackVO;
import com.pg.pgfplots.entity.Feedback;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 反馈表 Mapper */
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /** 管理员分页查询（含 username 与筛选） */
    List<FeedbackVO> selectAdminPage(@Param("type") String type,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    /** 管理员查询总数（与列表同条件） */
    long countAdmin(@Param("type") String type,
                    @Param("startDate") String startDate,
                    @Param("endDate") String endDate);

    /** 详情（含 username） */
    FeedbackVO selectDetail(@Param("feedbackId") Integer feedbackId);

    /** 用户自己的反馈列表（含 status） */
    List<FeedbackVO> selectUserFeedbacks(@Param("userId") Integer userId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);
}
