package com.pg.pgfplots.controller;

import com.pg.pgfplots.common.Result;
import com.pg.pgfplots.dto.feedback.FeedbackVO;
import com.pg.pgfplots.dto.feedback.ReplyRequest;
import com.pg.pgfplots.dto.feedback.SubmitFeedbackRequest;
import com.pg.pgfplots.security.SecurityUtils;
import com.pg.pgfplots.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 反馈接口：/api/feedback/*。
 * <p>管理员端点用 {@code @PreAuthorize} 做角色校验（对应 Node 的 checkAdmin）。</p>
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** 用户提交反馈（201） */
    @PostMapping
    public ResponseEntity<Result<Map<String, Object>>> submit(@RequestBody SubmitFeedbackRequest request) {
        Integer feedbackId = feedbackService.submit(SecurityUtils.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok("反馈提交成功", Map.of("feedback_id", feedbackId)));
    }

    /** 用户查看自己的反馈 */
    @GetMapping("/user/my-feedbacks")
    public Result<Map<String, Object>> myFeedbacks(@RequestParam(value = "page", required = false) String page,
                                                   @RequestParam(value = "limit", required = false) String limit) {
        return Result.ok("获取反馈列表成功", feedbackService.myFeedbacks(SecurityUtils.userId(), page, limit));
    }

    /** 管理员查看全部反馈 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> adminList(@RequestParam(value = "page", required = false) String page,
                                                 @RequestParam(value = "limit", required = false) String limit,
                                                 @RequestParam(value = "type", required = false) String type,
                                                 @RequestParam(value = "startDate", required = false) String startDate,
                                                 @RequestParam(value = "endDate", required = false) String endDate) {
        return Result.ok(feedbackService.adminList(page, limit, type, startDate, endDate));
    }

    /** 管理员查看反馈详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<FeedbackVO> detail(@PathVariable("id") String id) {
        return Result.ok("反馈获取成功", feedbackService.detail(id));
    }

    /** 管理员回复反馈 */
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<FeedbackVO> reply(@PathVariable("id") String id, @RequestBody ReplyRequest request) {
        return Result.ok("回复成功", feedbackService.reply(SecurityUtils.userId(), id, request.getAnswer()));
    }

    /** 管理员删除反馈 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable("id") String id) {
        feedbackService.delete(id);
        return Result.okMsg("反馈删除成功");
    }
}
