package com.pg.pgfplots.client;

import lombok.Getter;

/** 上游模型接口返回错误状态码。 */
@Getter
public class LlmApiException extends RuntimeException {

    /** 上游返回的 HTTP 状态码 */
    private final int statusCode;

    public LlmApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
