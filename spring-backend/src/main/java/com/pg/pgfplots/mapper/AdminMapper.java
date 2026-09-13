package com.pg.pgfplots.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理后台聚合查询 Mapper（AdminUser / AdminNotice / AdminLog / AdminStatic）。
 */
public interface AdminMapper {

    // ---------- AdminUser 统计 ----------
    List<Map<String, Object>> selectRoleStats();

    List<Map<String, Object>> selectRecentRegistrations();

    Map<String, Object> selectUserSummary();

    // ---------- AdminNotice ----------
    List<Map<String, Object>> selectAdminNotices(@Param("keyword") String keyword,
                                                 @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);

    long countAdminNotices(@Param("keyword") String keyword,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    Map<String, Object> selectNoticeDetail(@Param("noticeId") Integer noticeId);

    long countDistinctReadNotices();

    long countUnreadSystemNotices();

    List<Map<String, Object>> selectNoticeRecentStats();

    List<Map<String, Object>> selectNoticeAdminStats();

    Map<String, Object> selectNoticeSummary();

    // ---------- AdminLog ----------
    List<Map<String, Object>> selectApiLogs(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    List<Map<String, Object>> selectSystemLogs(@Param("status") String status,
                                               @Param("search") String search,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    long countSystemLogs(@Param("status") String status,
                         @Param("search") String search,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate);

    List<Map<String, Object>> selectSystemLogStats(@Param("since") String since);

    List<Map<String, Object>> selectRecentApiStats(@Param("since") LocalDateTime since);

    long countRecentErrors(@Param("since") LocalDateTime since);

    long countActiveUsers(@Param("since") LocalDateTime since);

    Map<String, Object> selectRecentFileStats(@Param("since") LocalDateTime since);

    // ---------- 表计数（tables-status / static） ----------
    long countUsers();

    long countDataFile();

    long countGenerationHistory();

    long countFeedback();

    long countApiLog();

    long countSystemLog();
}
