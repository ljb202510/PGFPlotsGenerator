package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员-用户管理：/api/admin/users/*。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户列表 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(value = "page", required = false) String page,
                                            @RequestParam(value = "pageSize", required = false) String pageSize,
                                            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok("获取用户列表成功", adminUserService.list(page, pageSize, keyword));
    }

    /** 重置密码为 666666 */
    @PatchMapping("/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable("id") String id) {
        return Result.ok("密码已重置为666666", adminUserService.resetPassword(id));
    }

    /** 删除用户（级联） */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable("id") String id) {
        return Result.ok("用户删除成功", adminUserService.delete(SecurityUtils.userId(), id));
    }

    /** 用户统计概览 */
    @GetMapping("/statistics/overview")
    public Result<Map<String, Object>> statistics() {
        return Result.ok("获取用户统计数据成功", adminUserService.statistics());
    }
}
