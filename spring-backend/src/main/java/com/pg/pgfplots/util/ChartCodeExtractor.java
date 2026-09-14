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
        String code = "";
        if (fenced.find()) {
            code = fenced.group(1).trim();
        } else {
            Matcher bare = BARE.matcher(aiReply);
            if (bare.find()) {
                code = bare.group(0).trim();
            }
        }
        return normalizeEscapes(code);
    }

    /** 从 reasoning 文本中兜底提取代码块，返回带围栏的代码，无则返回空串。 */
    public static String extractFencedBlock(String text) {
        Matcher matcher = FENCED.matcher(text == null ? "" : text);
        if (matcher.find() && !matcher.group(1).trim().isEmpty()) {
            return "```latex\n" + normalizeEscapes(matcher.group(1).trim()) + "\n```";
        }
        return "";
    }

    /**
     * [批次3.6] 还原被「压平」的换行 / 制表转义。
     * <p>部分模型不输出真实换行，而是输出字面的 {@code \n}（反斜杠 + n 两个字符），导致整段
     * {@code tikzpicture} 挤成一行、XeLaTeX 直接报错（批次3.5 评测中 {@code bar_dense} 稳定复现）。</p>
     * <p><b>不能朴素替换</b>：{@code \node} / {@code \newcommand} / {@code \newline} 与
     * {@code \text} / {@code \times} / {@code \tikz} 等大量合法命令都以 {@code \n} / {@code \t} 开头，
     * 误伤会产出更糟的代码。因此只还原「后面不跟 ASCII 字母」的转义；lookbehind 再排除前面已是
     * 反斜杠的情形（避免动到 LaTeX 换行 {@code \\} 之后紧跟 n 的写法）。</p>
     */
    public static String normalizeEscapes(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        return code.replaceAll("(?<!\\\\)\\\\n(?![A-Za-z])", "\n")
                .replaceAll("(?<!\\\\)\\\\t(?![A-Za-z])", "\t");
    }
}
