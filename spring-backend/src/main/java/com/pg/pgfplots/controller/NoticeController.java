package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口：/api/notice/*。
 */
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /** 通知列表 */
    @GetMapping
    public Result<Map<String, Object>> list() {
        return Result.ok("获取通知成功", noticeService.list(SecurityUtils.userId()));
    }

    /** 标记单条已读 */
    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable("id") String id) {
        noticeService.markRead(SecurityUtils.userId(), id);
        return Result.okMsg("标记已读成功");
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        noticeService.markAllRead(SecurityUtils.userId());
        return Result.okMsg("全部标记已读成功");
    }

    /** 未读数量 */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok("获取未读数量成功", noticeService.unreadCount(SecurityUtils.userId()));
    }
}
