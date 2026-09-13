package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.chat.ChatRequest;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 生成接口：POST /api/chat。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 生成图表代码 */
    @PostMapping
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        return Result.ok(chatService.generate(SecurityUtils.userId(), request));
    }
}
