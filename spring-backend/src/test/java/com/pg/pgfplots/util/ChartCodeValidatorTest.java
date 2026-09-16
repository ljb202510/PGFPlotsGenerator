package com.pg.pgfplots.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [v1.2] RAG 入库准入门槛的确定性验证。
 * <p>背景：history 315 / 318 / 329 三次「折线图」请求都生成了两个 {@code \addplot} 坐标完全相同的
 * 折线图，且该结果被写入 {@code rag_vector} 当作 few-shot 范例，导致之后每次同题请求都照抄同一份
 * 错误结果（自污染循环）。本校验用于把这类案例挡在索引之外。</p>
 */
class ChartCodeValidatorTest {

    @Test
    void detectsDuplicatedSeriesFromRealIncident() {
        // 取自真实事故记录 history 329（只保留结构，省略部分轴选项）
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n    symbolic x coords={2019, 2020, 2021, 2022, 2023},\n"
                + "    xtick=data\n]\n"
                + "\\addplot[blue, mark=*] coordinates {(2019,120.6) (2020,136.7) (2021,352.1) (2022,688.7) (2023,949.3)};\n"
                + "\\addlegendentry{产量}\n"
                + "\\addplot[red, mark=square*] coordinates {(2019,120.6) (2020,136.7) (2021,352.1) (2022,688.7) (2023,949.3)};\n"
                + "\\addlegendentry{销量}\n\\end{axis}\n\\end{tikzpicture}";
        assertTrue(ChartCodeValidator.hasDuplicateSeries(code));
    }

    @Test
    void acceptsTwoSeriesWithDistinctValues() {
        String code = "\\addplot[blue] coordinates {(2019,320) (2020,410) (2021,455)};\n"
                + "\\addplot[red] coordinates {(2019,300) (2020,392) (2021,440)};";
        assertFalse(ChartCodeValidator.hasDuplicateSeries(code));
    }

    @Test
    void acceptsSingleSeriesChart() {
        String code = "\\addplot[fill=blue!50] coordinates {(上海,4.72) (北京,4.37) (深圳,3.46)};";
        assertFalse(ChartCodeValidator.hasDuplicateSeries(code));
    }

    @Test
    void ignoresOrderAndWhitespaceDifferences() {
        // 同一组点、书写顺序与空白不同 → 仍应判定为重复
        String code = "\\addplot coordinates {(2019,120.6) (2020,136.7)};\n"
                + "\\addplot coordinates {(2020, 136.7) (2019, 120.6)};";
        assertTrue(ChartCodeValidator.hasDuplicateSeries(code));
    }

    @Test
    void acceptsThreeSeriesWhereOnlyTwoMatchButWithSinglePoint() {
        // 单点系列不足以构成「两条重合的线」，保守放行
        String code = "\\addplot coordinates {(2019,120.6) (2020,136.7)};\n"
                + "\\addplot coordinates {(2019,120.6) (2020,200)};\n"
                + "\\addplot coordinates {(2021,300)};";
        assertFalse(ChartCodeValidator.hasDuplicateSeries(code));
    }

    @Test
    void returnsFalseForNullOrEmptyOrTextWithoutPlot() {
        assertFalse(ChartCodeValidator.hasDuplicateSeries(null));
        assertFalse(ChartCodeValidator.hasDuplicateSeries(""));
        assertFalse(ChartCodeValidator.hasDuplicateSeries("这是一段没有坐标的说明文字。"));
    }

    // ==================== [语料治理] 入库准入规则集 violationsForIngest ====================

    /** 一条同时满足全部准入规则的正常柱状图代码（下面多处方可用它做"零误杀"基准）。 */
    private static final String WELL_FORMED_BAR = "\\begin{tikzpicture}\n"
            + "\\begin{axis}[\n"
            + "    ybar,\n"
            + "    title={各车间产量对比},\n"
            + "    ylabel={产量（台）},\n"
            + "    symbolic x coords={一车间,二车间,三车间},\n"
            + "    xtick=data,\n"
            + "    nodes near coords,\n"
            + "    every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south},\n"
            + "    legend style={at={(1.03,0.5)}, anchor=west}\n"
            + "]\n"
            + "\\addplot coordinates {(一车间,4520) (二车间,6100) (三车间,3890)};\n"
            + "\\end{axis}\n"
            + "\\end{tikzpicture}";

    @Test
    void passesWellFormedChartWithoutAnyViolation() {
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(WELL_FORMED_BAR));
    }

    @Test
    void acceptsNullAndEmptyAndBlank() {
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(null));
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(""));
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest("   \n  "));
    }

    @Test
    void rejectsNonCodeContentAsNoTikz() {
        // 取自真实事故：history 27（user_input="你是谁"）的 chart_code 实际是一段闲聊回复，
        // 却被兜底提取当作图表代码落库并索引进向量库。这里验证它会被 NO_TIKZ 拦下。
        String chitchat = "你好！我是你的专业图表生成助手，专注于根据你的需求和数据生成 **LaTeX PGFPlots** 代码。"
                + "\n\n如果你有数据（比如Excel文件、表格数据或描述），可以上传或告诉我，我会："
                + "\n1. **分析数据结构和内容**（列名、数据类型、范围等）。";
        assertEquals(List.of("NO_TIKZ"), ChartCodeValidator.violationsForIngest(chitchat));
    }

    @Test
    void hasTikzStructureDetectsCanvas() {
        assertTrue(ChartCodeValidator.hasTikzStructure(WELL_FORMED_BAR));
        assertTrue(ChartCodeValidator.hasTikzStructure("\\begin{tikzpicture}\\end{tikzpicture}"));
        assertFalse(ChartCodeValidator.hasTikzStructure("你好，我不是代码"));
        assertFalse(ChartCodeValidator.hasTikzStructure(null));
    }

    @Test
    void rejectsFullWidthCommaIsNotEnforced() {
        // 【零误杀】R7（全角逗号）已被 LatexCompiler.preprocess 的"全角逗号归一"确定性修复，
        // 故刻意不纳入入库准入——否则会把「实际能编译出正确图」的有效案例误杀。
        String code = "\\begin{tikzpicture}\n"
                + "\\begin{axis}[symbolic x coords={一季度，二季度}]\n"
                + "\\addplot coordinates {(一季度,45) (二季度,62)};\n"
                + "\\end{axis}\n"
                + "\\end{tikzpicture}";
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(code));
    }

    @Test
    void rejectsMissingYbarAndTightYmaxAreNotEnforced() {
        // 【零误杀】R10（直方图须 ybar）与 R11（ymax 覆盖数据）均已由 preprocess 的
        // addYbarForHistogram / ensureYmaxCoversData 在编译前修好，故同样不纳入入库准入。
        String histogramWithoutYbarAndTightYmax = "\\begin{tikzpicture}\n"
                + "\\begin{axis}[ymax=20, title={成绩分布}]\n"
                + "\\addplot[fill=blue!30] coordinates {(0-59,3) (60-69,18)};\n"
                + "\\end{axis}\n"
                + "\\end{tikzpicture}";
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(histogramWithoutYbarAndTightYmax));
    }

    @Test
    void detectsLegendPosition() {
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[legend pos=north west]\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R1_LEGEND_POS"));
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[legend to name=lg]\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R1_LEGEND_POS"));
    }

    @Test
    void detectsNodeOutsideAxis() {
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}\\addplot coordinates {(a,1) (b,2)};\\end{axis}\n"
                        + "\\node at (current bounding box.south) {来源};\\end{tikzpicture}")
                .contains("R2_NODE_OUTSIDE"));
    }

    @Test
    void detectsMarkWithoutValueLabels() {
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}\\addplot[mark=*] coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R3_NODES_PAIRED"));
        // 无效键
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[nodes near coords, nodes near coords style={anchor=south}, "
                        + "every node near coord/.append style={fill=none, draw=none}]\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R3_NODES_PAIRED"));
        // 缺少无边框样式（默认白底黑框会盖住数据点）
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[nodes near coords]\\addplot[mark=*] coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R3_NODES_PAIRED"));
    }

    @Test
    void detectsErrorBarSyntaxProblems() {
        // 有 error bars 但坐标没有 +-
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[error bars/.cd, y dir=both]\\addplot coordinates {(a,520) (b,568)};\\end{axis}\\end{tikzpicture}")
                .contains("R5_ERROR_BARS"));
        // 三元组 (x,y,err)
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}\\addplot +[error bars/.cd, y dir=both, y explicit] coordinates {(a,520,28) (b,568,35)};\\end{axis}\\end{tikzpicture}")
                .contains("R5_ERROR_BARS"));
    }

    @Test
    void detectsStructureViolations() {
        // 两个 tikzpicture
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\end{tikzpicture}\n\\begin{tikzpicture}\\end{tikzpicture}")
                .contains("R6_SINGLE_TIKZ"));
        // 浮动体 / 交叉引用
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{figure}\\begin{tikzpicture}\\caption{x}\\end{tikzpicture}\\end{figure}")
                .contains("R6_SINGLE_TIKZ"));
    }

    @Test
    void detectsLabelsNotStaggeredForMultiSeriesLine() {
        String base = "\\begin{tikzpicture}\n\\begin{axis}[nodes near coords]\n";
        // 两条系列的 y 值必须不同，否则会先命中 R9（多系列数据必须互不相同）
        String pointsA = "coordinates {(1,10) (2,20) (3,30) (4,40) (5,50) (6,60) (7,70) (8,80)}";
        String pointsB = "coordinates {(1,5) (2,15) (3,25) (4,35) (5,45) (6,55) (7,65) (8,75)}";
        // 两条系列都 anchor=south → 命中
        String sameAnchor = base
                + "\\addplot[blue, mark=*, every node near coord/.append style={fill=none, draw=none, anchor=south}] " + pointsA + ";\n"
                + "\\addplot[red, mark=*, every node near coord/.append style={fill=none, draw=none, anchor=south}] " + pointsB + ";\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertTrue(ChartCodeValidator.violationsForIngest(sameAnchor).contains("R8_LABEL_STAGGER"));

        // 上下错开 → 放行
        String staggered = base
                + "\\addplot[blue, mark=*, every node near coord/.append style={fill=none, draw=none, anchor=south}] " + pointsA + ";\n"
                + "\\addplot[red, mark=*, every node near coord/.append style={fill=none, draw=none, anchor=north}] " + pointsB + ";\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(staggered));
    }

    @Test
    void detectsDuplicateSeriesThroughRuleSet() {
        // 与 hasDuplicateSeries 同源：命中 R9
        String code = "\\begin{tikzpicture}\n\\begin{axis}\n"
                + "\\addplot[blue] coordinates {(2019,120.6) (2020,136.7) (2021,352.1)};\n"
                + "\\addplot[red] coordinates {(2019,120.6) (2020,136.7) (2021,352.1)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertTrue(ChartCodeValidator.violationsForIngest(code).contains("R9_DUPLICATE_SERIES"));
    }

    @Test
    void detectsUnescapedPercentInTextParams() {
        assertTrue(ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[ylabel={准确率（%）}]\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}")
                .contains("R12_UNESCAPED_PERCENT"));
        // 已转义 → 放行
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[ylabel={准确率（\\%）}]\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}"));
        // 注释行里的 % 是合法 LaTeX 注释，不应误判
        assertEquals(List.of(), ChartCodeValidator.violationsForIngest(
                "\\begin{tikzpicture}\\begin{axis}[ylabel={准确率}]\n% 这里 100% 是注释\n\\addplot coordinates {(a,1) (b,2)};\\end{axis}\\end{tikzpicture}"));
    }
}
