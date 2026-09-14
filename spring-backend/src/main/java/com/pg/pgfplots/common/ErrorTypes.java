package com.pg.pgfplots.common;

/**
 * 失败分类枚举值（批次3/E2）：写入 {@code api_log.error_type}，由管理端聚合「失败归类」计数。
 * <p>说明：批次3 起 {@code api_log} 的语义从「LLM 调用日志」扩展为「AI 链路失败事件日志」，
 * 因此除生成链路的分类外，还包含编译链路的 COMPILE_ERROR / COMPILE_QUEUE_FULL。</p>
 */
public final class ErrorTypes {

    private ErrorTypes() {
    }

    /** 上游 API 返回错误（LlmApiException） */
    public static final String UPSTREAM_API_ERROR = "UPSTREAM_API_ERROR";
    /** 无法连接上游（LlmConnectionException） */
    public static final String UPSTREAM_CONNECTION_ERROR = "UPSTREAM_CONNECTION_ERROR";
    /** 上游其他异常 */
    public static final String UPSTREAM_UNKNOWN_ERROR = "UPSTREAM_UNKNOWN_ERROR";
    /** 上游返回空内容（空回复兜底） */
    public static final String EMPTY_REPLY = "EMPTY_REPLY";
    /** 生成成功但未提取到可编译的图表代码 */
    public static final String CODE_EXTRACT_FAIL = "CODE_EXTRACT_FAIL";
    /** XeLaTeX 编译失败 */
    public static final String COMPILE_ERROR = "COMPILE_ERROR";
    /** 编译任务被线程池拒绝（队列已满） */
    public static final String COMPILE_QUEUE_FULL = "COMPILE_QUEUE_FULL";
}
