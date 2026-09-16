package com.pg.pgfplots.util;

import java.util.Set;
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
     * 误伤会产出更糟的代码。</p>
     * <p>[v2.2] <b>修正原判据的盲区</b>：原实现只还原「后面不跟 ASCII 字母」的 {@code \n}，
     * 于是 {@code ]\ncoordinates} 这种写法（{@code \n} 后紧跟 c）被放行 →
     * 真实事故：模板 {@code hist490} 的 {@code tikzpicture} 在 axis 选项处被截断，
     * 产出 <b>873 字节、2 页的全白 PDF</b>（编译"成功"、不报错，只有人工看图或产物检测才发现）。</p>
     * <p>现改为：{@code \n} 后面跟的字母串<b>若不构成已知的、以 n 开头的 LaTeX 命令</b>，一律按换行还原；
     * 白名单外的写法不再放行。lookbehind 仍排除前面已是反斜杠的情形（避免动到 {@code \\} 之后紧跟 n）。</p>
     */
    public static String normalizeEscapes(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        return normalizeLiteralN(code)
                .replaceAll("(?<!\\\\)\\\\t(?![A-Za-z])", "\t");
    }

    /** 字面 {@code \n} 及其后紧跟的字母串（字母串可能为空，如 {@code \n} 后是空格）。 */
    private static final Pattern ESC_LITERAL_N = Pattern.compile("(?<!\\\\)\\\\n([A-Za-z]*)");

    /**
     * 以 {@code n} 开头、真实存在的 LaTeX / pgfplots 命令名白名单。
     * <p>只收录「{@code \n} + 余下字母」确实构成合法命令的名字；不在此列的一律按换行还原。</p>
     */
    private static final Set<String> N_COMMANDS = Set.of(
            "node", "nodepart", "neq", "nequiv", "nearrow", "nwarrow", "nabla", "natural",
            "nolimits", "noindent", "nopagebreak", "nolinebreak", "nonumber", "notag", "notin",
            "notni", "nsubseteq", "nsupseteq", "ngtr", "nless", "nleq", "ngeq", "nmid",
            "nparallel", "nrightarrow", "nleftarrow", "nRightarrow", "nLeftarrow",
            "newline", "newpage", "normalsize", "nocite", "nullfont", "nobreak",
            "nobreakspace", "negthinspace", "negmedspace", "negthickspace", "not",
            "newcommand", "newenvironment", "newcounter", "newif", "newsavebox", "numberwithin");

    private static String normalizeLiteralN(String code) {
        Matcher m = ESC_LITERAL_N.matcher(code);
        if (!m.find()) {
            return code;
        }
        StringBuffer sb = new StringBuffer();
        do {
            String name = "n" + m.group(1);
            // 白名单命中 → 原样保留（如 \node）；否则只把 \n 换成换行，后面的字母串照旧保留
            String rep = N_COMMANDS.contains(name) ? m.group(0) : "\n" + m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }
}
