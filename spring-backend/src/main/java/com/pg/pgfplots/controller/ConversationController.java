package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.conversation.ConversationMessageVO;
import com.pg.pgfplots.dto.conversation.ConversationTitleRequest;
import com.pg.pgfplots.dto.conversation.ConversationVO;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话接口：/api/conversations/*。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 会话列表 */
    @GetMapping
    public Result<List<ConversationVO>> list() {
        return Result.ok(conversationService.list(SecurityUtils.userId()));
    }

    /** 新建会话 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody(required = false) ConversationTitleRequest request) {
        String title = request == null ? null : request.getTitle();
        return Result.ok(conversationService.create(SecurityUtils.userId(), title));
    }

    /** 重命名会话 */
    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable("id") String id,
                               @RequestBody(required = false) ConversationTitleRequest request) {
        String title = request == null ? null : request.getTitle();
        conversationService.rename(SecurityUtils.userId(), id, title);
        return Result.success();
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        conversationService.delete(SecurityUtils.userId(), id);
        return Result.success();
    }

    /** 会话消息 */
    @GetMapping("/{id}/messages")
    public Result<List<ConversationMessageVO>> messages(@PathVariable("id") String id) {
        return Result.ok(conversationService.messages(SecurityUtils.userId(), id));
    }
}
