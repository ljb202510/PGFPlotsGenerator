package com.pg.pgfplots.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [A3] 结构化输出解析（批次1）：优先解析模型返回的 JSON {@code {"chart_type","code","summary"}}。
 * <p>解析顺序：```json 围栏 → 裸 JSON 大括号；code 必须含 tikzpicture 才视为有效；
 * 任何失败返回 null，由调用方回退 {@link ChartCodeExtractor} 正则兜底——绝不因模型不听话而整体失败。</p>
 */
public final class StructuredOutputParser {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern BARE_JSON = Pattern.compile("\\{[\\s\\S]*\\}");

    private StructuredOutputParser() {
    }

    /** 解析结果：code 必须非空且含 tikzpicture 才有效 */
    public record Structured(String chartType, String code, String summary) {
    }

    /**
     * 解析结构化输出；无效返回 null（调用方走正则兜底）。
     */
    public static Structured parse(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) {
            return null;
        }
        String json = null;
        Matcher fenced = JSON_FENCE.matcher(aiReply);
        if (fenced.find()) {
            json = fenced.group(1);
        } else {
            Matcher bare = BARE_JSON.matcher(aiReply);
            if (bare.find()) {
                json = bare.group();
            }
        }
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = OM.readTree(json);
            // [批次3.6] Jackson 只解一层转义；若模型输出的是字面 \n（被压平），此处统一还原
            String code = ChartCodeExtractor.normalizeEscapes(root.path("code").asText("").trim());
            if (code.isEmpty() || !code.contains("tikzpicture")) {
                return null; // 结构不符 → 走兜底
            }
            return new Structured(root.path("chart_type").asText(""), code, root.path("summary").asText(""));
        } catch (Exception e) {
            return null;
        }
    }
}
