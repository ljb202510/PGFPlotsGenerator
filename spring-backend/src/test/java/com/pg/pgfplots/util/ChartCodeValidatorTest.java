package com.pg.pgfplots.util;

import org.junit.jupiter.api.Test;

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
}
