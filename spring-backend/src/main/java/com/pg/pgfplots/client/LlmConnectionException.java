package com.pg.pgfplots.client;

/** 无法连接上游模型接口（网络错误）。 */
public class LlmConnectionException extends RuntimeException {

    public LlmConnectionException(String message) {
        super(message);
    }
}
