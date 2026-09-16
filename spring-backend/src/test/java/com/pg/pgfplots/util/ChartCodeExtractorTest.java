package com.pg.pgfplots.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [批次3.6] 代码提取与「压平转义」还原的确定性验证。
 * <p>背景：评估集 {@code bar_dense} 用例中，模型把换行输出成字面 {@code \n}（反斜杠 + n），
 * 整段 {@code tikzpicture} 被压成一行，XeLaTeX 直接报错。还原只应针对转义本身，
 * <b>不得误伤</b> {@code \node} / {@code \newcommand} / {@code \text} / {@code \times}
 * 这类以 {@code \n} / {@code \t} 开头的合法命令。</p>
 */
class ChartCodeExtractorTest {

    @Test
    void restoresLiteralNewlineEscapes() {
        String pressed = "\\begin{tikzpicture}\\begin{axis}[\\n    ybar,\\n    width=12cm\\n]"
                + "\\n\\addplot coordinates {(1,1)};\\n\\end{axis}\\n\\end{tikzpicture}";
        String out = ChartCodeExtractor.normalizeEscapes(pressed);
        assertTrue(out.contains("\n    ybar,"), "字面 \\n 应还原为真实换行");
        assertFalse(out.contains("\\n"), "还原后不应残留字面 \\n");
    }

    @Test
    void keepsLatexCommandsStartingWithBackslashN() {
        // \node / \newcommand / \newline 均以 \n 开头，后面跟字母 → 不属于转义，不得还原
        String code = "\\node at (0,0) {a};\n\\newcommand{\\foo}{bar}\n\\newline";
        assertEquals(code, ChartCodeExtractor.normalizeEscapes(code));
    }

    @Test
    void keepsLatexCommandsStartingWithBackslashT() {
        // \text / \times / \tikz / \tiny 均以 \t 开头，后面跟字母 → 不得还原
        String code = "\\text{a} \\times \\tikz \\tiny";
        assertEquals(code, ChartCodeExtractor.normalizeEscapes(code));
    }

    @Test
    void keepsDoubleBackslashBeforeN() {
        // LaTeX 换行 \\ 之后紧跟 n：前面已是反斜杠，lookbehind 应拦住，不当作字面 \n 还原
        String code = "a\\\\nb";
        assertEquals(code, ChartCodeExtractor.normalizeEscapes(code));
    }

    @Test
    void extractRestoresEscapesFromFencedBlock() {
        String reply = "```latex\n\\begin{tikzpicture}\\n\\end{tikzpicture}\n```";
        assertEquals("\\begin{tikzpicture}\n\\end{tikzpicture}", ChartCodeExtractor.extract(reply));
    }

    @Test
    void extractReturnsEmptyWithoutCode() {
        assertEquals("", ChartCodeExtractor.extract(null));
        assertEquals("", ChartCodeExtractor.extract("这是一段没有代码的解释文字。"));
    }

    @Test
    void extractFencedBlockRestoresEscapes() {
        String reasoning = "```latex\n\\begin{tikzpicture}\\n\\end{tikzpicture}\n```";
        String out = ChartCodeExtractor.extractFencedBlock(reasoning);
        assertTrue(out.contains("\\begin{tikzpicture}\n\\end{tikzpicture}"), "reasoning 兜底路径同样应还原");
    }

    // ---------- [v2.2] 盲区修复：\n 后面跟字母、但整体不构成已知命令 ----------

    @Test
    void restoresLiteralNFollowedByLettersWhenNotACommand() {
        // 真实事故（模板 hist490）：...anchor=south}]\ncoordinates { 里 \n 后面紧跟 c，
        // 原判据「后面不跟 ASCII 字母」把它放行 → axis 选项在 ] 处被截断 → 整张图被吞掉，
        // 产出 873 字节 / 2 页的全白 PDF（编译却"成功"）。
        String pressed = "\\begin{axis}[\\n    ybar\\n]\\ncoordinates {(1,1)};";
        String out = ChartCodeExtractor.normalizeEscapes(pressed);
        assertTrue(out.contains("]\ncoordinates {(1,1)};"), "应还原为真实换行: " + out);
        assertFalse(out.contains("\\ncoordinates"), "不应残留字面 \\n");
    }

    @Test
    void restoresLiteralNBeforeWordsThatMerelyLookLikeCommands() {
        // "nbegin" / "naddplot" 都不是命令 → 还原为换行
        assertEquals("a\nbegin{axis}b", ChartCodeExtractor.normalizeEscapes("a\\nbegin{axis}b"));
        assertEquals("x\naddplot y", ChartCodeExtractor.normalizeEscapes("x\\naddplot y"));
    }

    @Test
    void keepsEveryWhitelistedNCommand() {
        // 白名单内的 \n 开头命令必须逐字保留（回归保护：不能为了修盲区而误伤）
        String code = "\\node[a] at (1,1) {x}; \\nodepart{lower} \\neq \\newline \\nodepart{two}"
                + " \\newcommand{\\x}{y} \\nobreak \\nsubseteq";
        assertEquals(code, ChartCodeExtractor.normalizeEscapes(code));
    }

    @Test
    void keepsLiteralNAtEndOrFollowedByNonLetter() {
        assertEquals("a\nb", ChartCodeExtractor.normalizeEscapes("a\\nb"));
        assertEquals("a\n", ChartCodeExtractor.normalizeEscapes("a\\n"));
        assertEquals("a\n1", ChartCodeExtractor.normalizeEscapes("a\\n1"));
    }
}
