package com.pg.pgfplots.service.rag;

/** RAG 依赖不可用的受控异常：只在 service/rag 包内部传递，由 RagService 统一吞掉降级，绝不外抛 */
public class RagUnavailableException extends RuntimeException {
    public RagUnavailableException(String message) {
        super(message);
    }

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
