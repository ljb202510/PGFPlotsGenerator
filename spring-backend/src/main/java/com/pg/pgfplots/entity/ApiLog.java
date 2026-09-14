package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 调用日志表 api_log */
@Data
@TableName("api_log")
public class ApiLog {

    @TableId(value = "call_id", type = IdType.AUTO)
    private Integer callId;

    private Integer userId;

    private Integer historyId;

    /** success | failed */
    private String callStatus;

    private LocalDateTime callTime;

    private String callError;

    /** 提示词版本号（批次1/A2），值取 PromptTemplates.VERSION */
    private String promptVersion;

    /** LLM 调用耗时（毫秒），批次2/O1；编译耗时记在编译任务终态，不落 api_log */
    private Long durationMs;

    /** 失败分类（批次3/E2），取值见 {@link com.pg.pgfplots.common.ErrorTypes}；成功调用为 null */
    private String errorType;
}
