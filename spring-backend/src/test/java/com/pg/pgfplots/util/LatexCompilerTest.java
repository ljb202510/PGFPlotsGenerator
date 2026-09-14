package com.pg.pgfplots.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [v1.4] 编译前预处理中「空 axis 剔除」的确定性验证。
 * <p>背景：真实生成的饼图用例里，模型把 {@code \pie} 包进了 {@code \begin{axis}}（内部一个
 * {@code \addplot} 都没有），渲染出一个 0–1 的空坐标系方框，饼图与图例看起来全落在坐标轴之外。
 * 剔除只应在「整段代码没有任何 addplot」时发生，含 addplot 的图一律不动。</p>
 */
class LatexCompilerTest {

    @Test
    void stripsEmptyAxisWrappingPieChart() {
        String code = "\\begin{tikzpicture}\n"
                + "\\begin{axis}[\n    title={2023年中国能源消费结构},\n    width=8cm\n]\n"
                + "\\end{axis}\n"
                + "\\pie[radius=2.5, text=legend]{55.3/煤炭, 18.7/石油}\n"
                + "\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("axis"), "空 axis 应被整体剔除");
        assertTrue(out.contains("\\pie[radius=2.5, text=legend]"), "饼图命令必须原样保留");
        assertTrue(out.contains("\\begin{tikzpicture}") && out.contains("\\end{tikzpicture}"));
    }

    @Test
    void keepsAxisWhenAddplotPresent() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot coordinates {(一车间,4520) (二车间,6100)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "含 addplot 的图一律不动");
    }

    @Test
    void keepsCodeWithoutAxis() {
        String code = "\\begin{tikzpicture}\n\\pie[radius=3, text=legend]{38/品牌A, 62/品牌B}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code));
    }

    // ---------------- [v1.5] 直方图补 ybar ----------------

    @Test
    void addsYbarWhenHistogramLacksIt() {
        // 取自真实事故：模型给了柱体填充色 fill=blue!70，却漏写 axis 的 ybar，
        // pgfplots 把填充色用在折线上闭合成多边形 → 渲染成面积覆盖图
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    title={2023年中国居民人均可支配收入分布直方图（单位：万元）},\n"
                + "    xlabel={收入区间（万元）},\n"
                + "    symbolic x coords={0-2,2-4,4-6,6-8,8-10,10-12,12-15,15-20,20+},\n"
                + "    xtick=data\n]\n"
                + "\\addplot[fill=blue!70, draw=black] coordinates {\n"
                + "    (0-2,2.5) (2-4,6.2) (4-6,8.8) (6-8,10.5) (8-10,12.1) (10-12,10.8) (12-15,8.5) (15-20,4.2) (20+,1.8)\n};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("\\begin{axis}[ybar"), "直方图缺 ybar 时应自动补上");
        assertTrue(out.contains("fill=blue!70"), "柱体填充色必须保留");
    }

    @Test
    void keepsAreaChartWithoutHistogramKeyword() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n    title={某指标趋势}\n]\n"
                + "\\addplot[fill=blue!20] coordinates {(1,1) (2,2) (3,3)};\n\\end{axis}\n\\end{tikzpicture}";
        assertFalse(LatexCompiler.preprocess(code).contains("ybar"), "不含直方图/分布关键词的图不得被改动");
    }

    @Test
    void keepsHistogramThatAlreadyHasYbar() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar, title={成绩分布直方图}]\n"
                + "\\addplot[fill=blue!50] coordinates {(0-59,3) (60-69,8)};\n\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "已有 ybar 的直方图不得重复补");
    }

    // ---------------- [v1.6] ymax 必须覆盖全部数据 ----------------

    @Test
    void raisesYmaxWhenDataOverflowsAxis() {
        // 真实事故：ymax=40000 而北京=40184 → 柱子被轴顶裁掉、顶部标注不可见
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar, ymin=0, ymax=40000]\n"
                + "\\addplot[fill=blue!70] coordinates {(北京,40184) (上海,38100)};\n\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("ymax=44203"), "ymax 应被抬到 最大值×1.1（留 10% 余量），实际: " + out);
        assertFalse(out.contains("ymax=40000"), "旧的不够用的 ymax 必须被替换");
    }

    @Test
    void keepsYmaxThatAlreadyCoversData() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar, ymax=200]\n"
                + "\\addplot[fill=blue!70] coordinates {(A,90) (B,100)};\n\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "ymax 已覆盖数据时不得改动");
    }

    @Test
    void keepsMissingYmaxUntouched() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot[fill=blue!70] coordinates {(A,90) (B,100)};\n\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "未显式写 ymax 时交给 pgfplots 自动计算，不干预");
    }

    // ---------------- [v1.7] 非法 color 键 / 未加花括号的 at 坐标 ----------------

    @Test
    void dropsColorKeyHoldingColorList() {
        // 真实事故：color= 只接受单一颜色，逗号列表会被 xcolor 当成一个颜色名
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar, bar width=0.4cm,\n"
                + "    color={blue, red, green, orange, purple}\n]\n"
                + "\\addplot coordinates {(A,1200) (B,950)};\n\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("blue, red, green"), "逗号颜色列表必须被删除: " + out);
        // 关键：必须连同整行删除。只删键会留下一行只有空白的文本，
        // 而 TeX 丢弃行尾空白 → 「只有空白的行」等价于空行（\par）→ 中断 axis 选项解析。
        assertTrue(out.contains("bar width=0.4cm,\n]"),
                "删除后不得在 axis 选项区留下空行: " + out);
        assertTrue(out.contains("\\addplot coordinates {(A,1200) (B,950)}"));
    }

    @Test
    void keepsSingleColorKey() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar, color=blue]\n"
                + "\\addplot coordinates {(A,1) (B,2)};\n\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "单一颜色的 color= 必须保留");
    }

    @Test
    void bracesUnbracedAtCoordinate() {
        // 真实事故：at=(1.03,0.5) 的逗号被当成键分隔符 → Runaway argument
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar,\n"
                + "    legend style={at=(1.03,0.5), anchor=west}\n]\n"
                + "\\addplot coordinates {(A,1) (B,2)};\n\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("at={(1.03,0.5)}"), "未加花括号的 at 坐标必须补上花括号: " + out);
    }

    // ---------------- 文档外壳：xcolor 命名色选项 ----------------

    @Test
    void declaresNamedColorOptionsBeforePgfplots() {
        // 真实事故：曾把选项写在 {xcolor} 之后（形如 ...{xcolor}[dvipsnames,svgnames]），
        // 选项不生效，steelblue/teal/coral 等命名色从未加载，残留文本还在 preamble 被当正文排版
        String doc = LatexCompiler.buildDocument("\\begin{tikzpicture}\\end{tikzpicture}");
        assertTrue(doc.contains("\\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}"),
                "命名色表须以 PassOptionsToPackage 声明: " + doc);
        assertFalse(doc.contains("{xcolor}["),
                "选项写在 {xcolor} 之后不生效: " + doc);
        assertTrue(doc.indexOf("\\PassOptionsToPackage") < doc.indexOf("\\usepackage{pgfplots}"),
                "必须在 pgfplots 之前声明，否则 xcolor 已被其先加载");
    }

    @Test
    void keepsAlreadyBracedAtCoordinate() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar,\n"
                + "    legend style={at={(1.03,0.5)}, anchor=west}\n]\n"
                + "\\addplot coordinates {(A,1) (B,2)};\n\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "已带花括号的 at 坐标不得重复补");
    }
}
