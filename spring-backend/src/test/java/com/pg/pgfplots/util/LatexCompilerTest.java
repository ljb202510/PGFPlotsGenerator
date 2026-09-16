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

    // ---------------- [v1.8] X 轴分类标签旋转兜底（回归 2026-09-14：用例 30/31/27） ----------------

    @Test
    void rotatesLongXTickLabelsOnDenseYbar() {
        // 用例 31：5 个长中文分类、AI 未写 x tick label style → 底部标签重叠
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    ybar,\n"
                + "    symbolic x coords={官方商城, 直播带货, 社群团购, 线下门店, 老客推荐},\n"
                + "    xtick=data\n]\n"
                + "\\addplot coordinates {(官方商城,3.25) (直播带货,5.68)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("x tick label style={font=\\scriptsize, rotate=30, anchor=east}"),
                "密集长分类应自动注入旋转 30°，实际: " + out);
    }

    @Test
    void overridesRotateZeroStyles() {
        // 用例 30：AI 写了 rotate=0（等于没旋转）→ 判断为事故写法，覆盖为旋转 30°
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    ybar,\n"
                + "    symbolic x coords={地铁三期工程, 跨江大桥, 老旧小区改造, 智慧交通平台, 城市绿道工程, 社区公园},\n"
                + "    x tick label style={font=\\small, rotate=0, align=center}\n]\n"
                + "\\addplot coordinates {(地铁三期工程,980000) (跨江大桥,326000)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("x tick label style={font=\\scriptsize, rotate=30, anchor=east}"),
                "rotate=0 是导致重叠的错误写法，应被覆盖为旋转 30°，实际: " + out);
        assertFalse(out.contains("rotate=0"), "rotate=0 必须被替换");
    }

    @Test
    void respectsNonZeroRotation() {
        // 作者已显式选择非零旋转 → 尊重，不做覆盖
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    ybar,\n"
                + "    symbolic x coords={地铁三期工程, 跨江大桥, 老旧小区改造, 智慧交通平台, 城市绿道工程, 社区公园},\n"
                + "    x tick label style={font=\\small, rotate=-45, anchor=east}\n]\n"
                + "\\addplot coordinates {(地铁三期工程,980000) (跨江大桥,326000)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "非零旋转体现作者意图，不得覆盖");
    }

    @Test
    void keepsFewCategoriesWithoutRotation() {
        // 分类数 < 6（如用例 25 之外的常规小图）一律不动
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar,\n"
                + "    symbolic x coords={一季度,二季度,三季度,四季度},\n"
                + "    xtick=data\n]\n"
                + "\\addplot coordinates {(一季度,45) (二季度,60) (三季度,75) (四季度,90)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "分类 < 6 时不注入旋转");
    }

    // ---------------- [v1.9] 合并「为区分正负值而拆分的 addplot」（回归 2026-09-14：用例 27） ----------------

    @Test
    void mergesSplitAddplotsForNegativeValues() {
        // 用例 27：AI 为区分正/负利润拆成两个 addplot，x 两两互不重叠 → 应合并回一个
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    ybar,\n"
                + "    symbolic x coords={2023Q1,2023Q2,2023Q3,2023Q4,2024Q1,2024Q2,2024Q3,2024Q4},\n"
                + "    xtick=data\n]\n"
                + "% 正利润\n"
                + "\\addplot[fill=blue!60, nodes near coords, every node near coord/.append style={anchor=south}] coordinates {\n"
                + "    (2023Q1,120) (2023Q3,210) (2024Q1,260) (2024Q2,180) (2024Q4,320)\n};\n"
                + "% 负利润\n"
                + "\\addplot[fill=red!60, nodes near coords, every node near coord/.append style={anchor=north}] coordinates {\n"
                + "    (2023Q2,-85) (2023Q4,-40) (2024Q3,-30)\n};\n"
                + "\\addlegendentry{净利润}\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        // 合并后只剩一个 addplot，且包含全部 8 个点
        long addplots = out.split("\\\\addplot", -1).length - 1;
        assertEquals(1, addplots, "拆分的 addplot 应被合并为 1 个，实际: " + out);
        assertTrue(out.contains("(2023Q2,-85)"), "负值点必须保留");
        assertTrue(out.contains("(2024Q4,320)"), "正值点必须保留");
        assertFalse(out.contains("fill=red"), "合并后不应再保留第二个 addplot 的红色填充");
    }

    @Test
    void keepsGenuineMultiSeriesUntouched() {
        // 真多系列：两个 addplot 的 x 坐标重叠（同一 X 两根柱）→ 绝不合并
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot[fill=blue!50] coordinates {(Q1,100) (Q2,120) (Q3,140)};\n"
                + "\\addplot[fill=red!50] coordinates {(Q1,80) (Q2,90) (Q3,110)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        assertEquals(code, LatexCompiler.preprocess(code), "x 重叠的多系列图不得合并");
    }

    // ---------------- [v2.0] 密集柱状图长数字缩写为「万」（回归 2026-09-14：用例 25） ----------------

    @Test
    void abbreviatesDenseLongNumberLabelsToWan() {
        // 用例 25：14 省 GDP（亿元）完整长数字 135673 → 13.6万
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n"
                + "    ybar,\n"
                + "    symbolic x coords={广东,江苏,山东,浙江,四川,河南,湖北,福建,湖南,上海,安徽,河北,北京,陕西},\n"
                + "    nodes near coords\n]\n"
                + "\\addplot[fill=blue!60, mark=*] coordinates {\n"
                + "    (广东,135673) (江苏,128222) (山东,92069) (浙江,82553) (四川,60132) (河南,59132) (湖北,55803)\n"
                + "    (福建,54355) (湖南,50012) (上海,47218) (安徽,47050) (河北,43944) (北京,43760) (陕西,33786)\n};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("point meta=explicit symbolic"), "应注入 point meta=explicit symbolic: " + out);
        assertTrue(out.contains("(广东,135673)[13.6万]"), "135673 应缩写为 13.6万: " + out);
        assertTrue(out.contains("(陕西,33786)[3.4万]"), "33786 应缩写为 3.4万: " + out);
        assertTrue(out.contains("135673"), "y 真实值必须保留以保持柱高正确");
        assertTrue(out.contains("font=\\tiny"), "密集单柱图应注入 \\tiny 缩字号防重叠: " + out);
        assertTrue(out.contains("inner sep=0pt"), "密集单柱图应清空标注内边距进一步防重叠: " + out);
    }

    @Test
    void keepsDenseShortNumbersWithoutAbbreviation() {
        // 坐标多但数值小（<10000）→ 不缩写（注意 N≥7 时会注入 enlarge x limits，故用 contains 断言）
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar,\n"
                + "    symbolic x coords={一,二,三,四,五,六,七,八},\n"
                + "    nodes near coords\n]\n"
                + "\\addplot coordinates {(一,120) (二,90) (三,80) (四,75) (五,60) (六,50) (七,40) (八,30)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("万]"), "数值 <10000 时不得缩写: " + out);
        assertTrue(out.contains("(一,120)"), "坐标必须原样保留");
        assertFalse(out.contains("point meta=explicit symbolic"), "不应注入 point meta: " + out);
    }

    @Test
    void keepsExistingLabelsUntouched() {
        // 已带 [label]（AI 已用 point meta 缩写）→ 不再重复缩写
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar,\n"
                + "    symbolic x coords={广东,江苏,山东,浙江,四川,河南,湖北,福建,湖南,上海,安徽,河北,北京,陕西},\n"
                + "    nodes near coords\n]\n"
                + "\\addplot[fill=blue!60, point meta=explicit symbolic] coordinates {\n"
                + "    (广东,135673)[13.6万] (江苏,128222)[12.8万] (山东,92069)[9.2万] (浙江,82553)[8.3万]\n"
                + "    (四川,60132)[6万] (河南,59132)[5.9万] (湖北,55803)[5.6万] (福建,54355)[5.4万]\n"
                + "    (湖南,50012)[5万] (上海,47218)[4.7万] (安徽,47050)[4.7万] (河北,43944)[4.4万]\n"
                + "    (北京,43760)[4.4万] (陕西,33786)[3.4万]\n};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        // 统计 [13.6万] 出现次数，应恰好 1 次（未重复缩写）
        assertEquals(1, countOccurrences(out, "[13.6万]"), "已带 label 不得重复缩写: " + out);
        assertTrue(out.contains("point meta=explicit symbolic"), "原有的 point meta 应保留");
    }

    // ---------- [v2.2] 水平条形图坐标顺序（模板 hist477 稳定复现） ----------

    @Test
    void swapsHorizontalBarCoordinatesWrittenInWrongOrder() {
        // xbar + symbolic y coords：pgfplots 要求 (数值, 分类)，写成 (分类, 数值) 会让数据点静默全丢
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n    xbar,\n"
                + "    symbolic y coords={上海,北京},\n    ytick=data\n]\n"
                + "\\addplot[fill=blue!60] coordinates {\n    (上海,831)\n    (北京,807)\n};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("(831,上海)"), "应交换为 (数值,分类): " + out);
        assertTrue(out.contains("(807,北京)"), "所有坐标都应交换: " + out);
        assertFalse(out.contains("(上海,831)"), "不应残留错误顺序: " + out);
    }

    @Test
    void keepsCorrectHorizontalBarCoordinates() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n    xbar,\n"
                + "    symbolic y coords={上海,北京}\n]\n"
                + "\\addplot coordinates {(831,上海) (807,北京)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("(831,上海)"), "已是正确顺序不得被改动: " + out);
        assertFalse(out.contains("(上海,831)"), "不得反向交换: " + out);
    }

    @Test
    void doesNotTouchVerticalBarCoordinates() {
        // 竖柱（ybar）用的是 symbolic x coords，(分类,数值) 本来就是正确写法 → 绝不能交换
        String code = "\\begin{tikzpicture}\n\\begin{axis}[\n    ybar,\n"
                + "    symbolic x coords={广东,江苏}\n]\n"
                + "\\addplot coordinates {(广东,135673) (江苏,128222)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("(广东,135673)"), "竖柱坐标不得被交换: " + out);
    }

    // ---------- [v2.2] xcolor 未定义色名（模板 hist477：steelblue 静默变黑） ----------

    @Test
    void fixesUndefinedLowercaseColorName() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot[fill=steelblue, draw=steelblue] coordinates {(1,1)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("fill=SteelBlue"), "未定义的小写色名应换成规范写法: " + out);
        assertTrue(out.contains("draw=SteelBlue"), "draw 同样应替换: " + out);
        assertFalse(out.contains("steelblue"), "不应残留未定义色名: " + out);
    }

    @Test
    void keepsValidColorExpressions() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot[fill=blue!60, draw=black] coordinates {(1,1)};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertTrue(out.contains("fill=blue!60"), "基色混合写法不得被改: " + out);
        assertTrue(out.contains("draw=black"), "合法基色不得被改: " + out);
    }

    // ---------- [v2.2] 数据来源注记位置归一（14 份模板中命中 10 例） ----------

    @Test
    void movesSourceNoteOutOfAxis() {
        // 写在 axis 内：不报错，但文本进 nullfont → PDF 里能提取到、画面上什么都没有
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot coordinates {(1,1)};\n"
                + "\\node[anchor=north west, font=\\scriptsize] at (axis description cs:0.0,-0.15)"
                + " {数据来源：内部统计};\n"
                + "\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("axis description cs"), "不应再依赖 axis 坐标系: " + out);
        assertTrue(out.contains("at (current bounding box.south west)"), "应改用稳定锚点: " + out);
        assertTrue(out.contains("{数据来源：内部统计}"), "注记文本必须保留: " + out);
        assertTrue(out.indexOf("\\node[anchor=north west") > out.indexOf("\\end{axis}"),
                "注记应位于 \\end{axis} 之后: " + out);
    }

    @Test
    void normalizesSourceNoteThatIsAlreadyAfterAxis() {
        // 写在 \\end{axis} 之后：坐标系已失效 → ! Undefined control sequence，位置只是碰巧对
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot coordinates {(1,1)};\n"
                + "\\end{axis}\n"
                + "\\node[anchor=north west] at (axis description cs:0.0,-0.15) {数据来源：x};\n"
                + "\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("axis description cs"), "写在 axis 之后同样要归一: " + out);
        assertTrue(out.contains("at (current bounding box.south west)"));
        assertEquals(1, countOccurrences(out, "数据来源：x"), "注记不得被复制或丢失: " + out);
    }

    @Test
    void leavesCodeWithoutSourceNoteUntouched() {
        String code = "\\begin{tikzpicture}\n\\begin{axis}[ybar]\n"
                + "\\addplot coordinates {(1,1)};\n\\end{axis}\n\\end{tikzpicture}";
        String out = LatexCompiler.preprocess(code);
        assertFalse(out.contains("current bounding box"), "无注记时不得凭空插入: " + out);
    }

    private static int countOccurrences(String s, String sub) {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
