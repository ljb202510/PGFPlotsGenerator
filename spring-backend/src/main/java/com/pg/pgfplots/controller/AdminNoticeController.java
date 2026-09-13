package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.AdminNoticeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理员-通知管理：/api/admin/notices/*。
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    /** 通知列表 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(value = "page", required = false) String page,
                                            @RequestParam(value = "pageSize", required = false) String pageSize,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "startDate", required = false) String startDate,
                                            @RequestParam(value = "endDate", required = false) String endDate) {
        return Result.ok("获取通知列表成功",
                adminNoticeService.list(page, pageSize, keyword, startDate, endDate));
    }

    /** 通知详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") String id) {
        return Result.ok("获取通知详情成功", adminNoticeService.detail(id));
    }

    /** 创建通知 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody NoticeRequest request) {
        return Result.ok("通知创建成功",
                adminNoticeService.create(SecurityUtils.userId(), request.getTitle(), request.getContent()));
    }

    /** 更新通知 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") String id, @RequestBody NoticeRequest request) {
        adminNoticeService.update(id, request.getTitle(), request.getContent());
        return Result.okMsg("通知更新成功");
    }

    /** 批量删除通知 */
    @DeleteMapping
    public Result<Void> batchDelete(@RequestBody BatchDeleteRequest request) {
        adminNoticeService.batchDelete(request.getIds());
        return Result.okMsg("批量删除成功");
    }

    /** 删除单条通知 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        adminNoticeService.delete(id);
        return Result.okMsg("通知删除成功");
    }

    /** 通知统计概览 */
    @GetMapping("/statistics/overview")
    public Result<Map<String, Object>> statistics() {
        return Result.ok("获取统计数据成功", adminNoticeService.statistics());
    }

    @Data
    public static class NoticeRequest {
        private String title;
        private String content;
    }

    @Data
    public static class BatchDeleteRequest {
        private List<Integer> ids;
    }
}
