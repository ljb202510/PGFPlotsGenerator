package com.pg.pgfplots.dto.conversation;

import lombok.Data;

/** 会话标题请求（新建/重命名共用）。 */
@Data
public class ConversationTitleRequest {
    private String title;
}
