package com.pg.pgfplots.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单次模型调用结果。
 *
 * @param content   正文内容
 * @param reasoning 思考内容（reasoning / reasoning_content）
 * @param usage     token 用量（原样透传给前端）
 */
public record LlmResponse(String content, String reasoning, JsonNode usage) {
}
