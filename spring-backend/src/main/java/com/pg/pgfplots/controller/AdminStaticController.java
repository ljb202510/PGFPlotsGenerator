package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员-概览统计：/api/admin/static。
 */
@RestController
@RequestMapping("/api/admin/static")
@RequiredArgsConstructor
public class AdminStaticController {

    private final AdminMapper adminMapper;

    /** 全库计数（用户/文件/生成/反馈） */
    @GetMapping
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", adminMapper.countUsers());
        data.put("files", adminMapper.countDataFile());
        data.put("generations", adminMapper.countGenerationHistory());
        data.put("feedback", adminMapper.countFeedback());
        return Result.ok("统计数据获取成功", data);
    }
}
