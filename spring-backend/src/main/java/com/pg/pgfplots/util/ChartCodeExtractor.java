package com.pg.pgfplots.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图表代码提取，对应 Node 的 {@code extractChartCode} 与 {@code extractCodeFromText}。
 */
public final class ChartCodeExtractor {

    private static final Pattern FENCED = Pattern.compile("```(?:latex|tex)?\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern BARE =
            Pattern.compile("\\\\begin\\{tikzpicture\\}[\\s\\S]*?\\\\end\\{tikzpicture\\}");

    private ChartCodeExtractor() {
    }

    /** 优先匹配 ```latex/tex 围栏，再兜底裸 tikzpicture；无则返回空串。 */
    public static String extract(String aiReply) {
        if (aiReply == null || aiReply.isEmpty()) {
            return "";
        }
        Matcher fenced = FENCED.matcher(aiReply);
        if (fenced.find()) {
            return fenced.group(1).trim();
        }
        Matcher bare = BARE.matcher(aiReply);
        if (bare.find()) {
            return bare.group(0).trim();
        }
        return "";
    }

    /** 从 reasoning 文本中兜底提取代码块，返回带围栏的代码，无则返回空串。 */
    public static String extractFencedBlock(String text) {
        Matcher matcher = FENCED.matcher(text == null ? "" : text);
        if (matcher.find() && !matcher.group(1).trim().isEmpty()) {
            return "```latex\n" + matcher.group(1).trim() + "\n```";
        }
        return "";
    }
}
