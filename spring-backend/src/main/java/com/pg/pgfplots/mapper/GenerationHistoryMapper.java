package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.dto.history.HistoryRecordVO;
import com.pg.pgfplots.entity.GenerationHistory;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 生成历史表 Mapper（含联表查询）。 */
public interface GenerationHistoryMapper extends BaseMapper<GenerationHistory> {

    /** 分页查询历史（LEFT JOIN data_file，支持可选日期过滤） */
    List<HistoryRecordVO> selectHistoryPage(@Param("userId") Integer userId,
                                            @Param("search") String search,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    /** 统计总数（与列表同条件） */
    long countHistory(@Param("userId") Integer userId,
                      @Param("search") String search,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /** 查询单条历史详情 */
    HistoryRecordVO selectHistoryDetail(@Param("historyId") Integer historyId,
                                        @Param("userId") Integer userId);
}
