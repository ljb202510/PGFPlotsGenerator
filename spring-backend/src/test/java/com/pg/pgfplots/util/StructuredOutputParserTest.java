package com.pg.pgfplots.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批次1 自检：A3 结构化输出解析器的确定性验证。
 * 兜底链的活体行为（真实 LLM 返回散文）由集成验证覆盖，此处验证解析规则本身。
 * 余弦相似度测试见 com.pg.pgfplots.service.rag.RetrieverTest。
 */
class StructuredOutputParserTest {

    private static final String TIKZ = "\\begin{tikzpicture}\n\\begin{axis}[title=test]\n\\addplot coordinates {(1,1)};\n\\end{axis}\n\\end{tikzpicture}";

    private static String jsonEscaped(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    @Test
    void parseFencedJsonBlock() {
        String reply = "```json\n{\"chart_type\":\"bar\",\"code\":\"" + jsonEscaped(TIKZ)
                + "\",\"summary\":\"柱状图\"}\n```\n```latex\n" + TIKZ + "\n```";
        StructuredOutputParser.Structured s = StructuredOutputParser.parse(reply);
        assertNotNull(s);
        assertEquals("bar", s.chartType());
        assertEquals("柱状图", s.summary());
        assertTrue(s.code().contains("tikzpicture"));
    }

    @Test
    void parseBareJsonFallback() {
        String reply = "{\"chart_type\":\"line\",\"code\":\"" + jsonEscaped(TIKZ) + "\",\"summary\":\"折线\"}";
        StructuredOutputParser.Structured s = StructuredOutputParser.parse(reply);
        assertNotNull(s);
        assertEquals("line", s.chartType());
    }

    @Test
    void proseReturnsNull() {
        // 散文（无 JSON、无围栏）→ null，走 ChartCodeExtractor 正则兜底
        assertNull(StructuredOutputParser.parse("这是一段没有代码的解释文字。"));
    }

    @Test
    void jsonWithoutTikzReturnsNull() {
        // JSON 结构不符（code 缺 tikzpicture）→ null
        assertNull(StructuredOutputParser.parse("```json\n{\"chart_type\":\"bar\",\"code\":\"not code\",\"summary\":\"x\"}\n```"));
    }

    @Test
    void nullAndBlankReturnNull() {
        assertNull(StructuredOutputParser.parse(null));
        assertNull(StructuredOutputParser.parse("   "));
    }
}
