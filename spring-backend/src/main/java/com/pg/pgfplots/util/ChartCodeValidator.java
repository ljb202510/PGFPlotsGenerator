package com.pg.pgfplots.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图表代码质量校验。
 * <p>[v1.2] 对应提示词规则 R9：多系列数据必须互不相同 —— {@link #hasDuplicateSeries(String)}。</p>
 * <p>[语料治理] 新增入库准入规则集 {@link #violationsForIngest(String)}：在写入 RAG 向量库之前拦截
 * 「明显无效」的案例，避免无效/错误案例被当作 few-shot 范例反复喂回模型
 * （自我强化循环：下次同题请求直接照抄上一次的结果）。</p>
 * <p>设计原则：<b>只做保守、可判定的检查；能判定的判不合格，判不准的一律放行，绝不误伤正常图。</b></p>
 *
 * <h3>两套规则集用途不同、集合不同，不得混淆</h3>
 * <ul>
 *   <li><b>入库准入</b>（本类 {@code violationsForIngest}）—— 用来<b>拒绝</b>无效案例。
 *       刻意<b>排除</b>已被编译前预处理兜底的规则（R7 全角逗号、R10 直方图 ybar、R11 ymax 覆盖），
 *       因为它们会被 {@link LatexCompiler#preprocess(String)} 确定性修好，纳入只会误杀
 *       「实际能产出正确图」的有效案例。</li>
 *   <li><b>生成质量知识</b>（{@link PromptTemplates} 的 R1–R12）—— 用来<b>教模型怎么写</b>，全含 12 条。
 *       排除 R7/R10/R11 <b>不代表</b>它们不重要。</li>
 * </ul>
 * <p>另注：离线评估脚本 {@code eval/violations.mjs} 覆盖 R1–R3、R5–R9、R12（<b>含 R7</b>）。
 * 评估侧与准入侧<b>结论一致但理由不同</b>（评估衡量模型原始能力、准入衡量实际产出质量），
 * 因此两侧集合并不完全相同，不追求「评估口径等于入库口径」。</p>
 * <p>判定口径与 {@code eval/violations.mjs} 逐条对齐（正则含义、扫描范围、边界条件）。</p>
 */
public final class ChartCodeValidator {

    /** 每个 \addplot 片段内取第一处 coordinates {...} */
    private static final Pattern COORDINATES = Pattern.compile("coordinates\\s*\\{([^}]*)\\}");

    /** 全部 coordinates {...} 块（用于估算 X 轴点数，对齐 violations.mjs 的全局匹配） */
    private static final Pattern COORDINATES_GLOBAL = Pattern.compile("coordinates\\s*\\{([\\s\\S]*?)\\}");

    /** symbolic x coords={...} 的分类列表 */
    private static final Pattern SYMBOLIC_COORDS = Pattern.compile("symbolic\\s+x\\s+coords\\s*=\\s*\\{([^}]*)\\}");

    /** 坐标点 (x,y)；x / y 内部不含括号，兼容 (2019,120.6) 与 (一季度,45) */
    private static final Pattern POINT = Pattern.compile("\\(([^(),]+),([^()]+)\\)");

    // ==================== 入库准入规则集用到的判据 ====================

    /** 最基础判据：必须含 tikz 画布 */
    private static final Pattern TIKZ_BEGIN = Pattern.compile("\\\\begin\\{tikzpicture\\}");

    /** R1 图例不遮挡数据：legend pos=north west / north east */
    private static final Pattern R1_LEGEND_POS =
            Pattern.compile("legend\\s+pos\\s*=\\s*(?:north\\s+west|north\\s+east)", Pattern.CASE_INSENSITIVE);

    /** R1 图例不遮挡数据：legend to name=… 暂存后另处引用（另处引用位置不可控） */
    private static final Pattern R1_LEGEND_TO_NAME =
            Pattern.compile("legend\\s+to\\s+name\\s*=", Pattern.CASE_INSENSITIVE);

    /** R2 轴外不写文字：\node at (current bounding box.*) */
    private static final Pattern R2_NODE_BOUNDING_BOX =
            Pattern.compile("\\\\node\\s+at\\s*\\(\\s*current\\s+bounding\\s+box", Pattern.CASE_INSENSITIVE);

    /** R3 数值标注与数据点标记成对出现 */
    private static final Pattern R3_MARK = Pattern.compile("\\bmark\\s*=");
    private static final Pattern R3_NODES_NEAR_COORDS = Pattern.compile("nodes\\s+near\\s+coords");
    private static final Pattern R3_INVALID_KEY = Pattern.compile("nodes\\s+near\\s+coords\\s+style\\s*=");
    private static final Pattern FILL_NONE = Pattern.compile("fill\\s*=\\s*none");
    private static final Pattern DRAW_NONE = Pattern.compile("draw\\s*=\\s*none");

    /** R5 误差棒语法 */
    private static final Pattern R5_ERROR_BARS = Pattern.compile("error\\s+bars", Pattern.CASE_INSENSITIVE);
    private static final Pattern R5_PLUS_MINUS = Pattern.compile("\\+-");
    private static final Pattern R5_TRIPLE = Pattern.compile("\\(\\s*[^(),]+\\s*,\\s*[^(),]+\\s*,\\s*[^(),]+\\s*\\)");

    /** R6 单图结构：浮动体与交叉引用 */
    private static final Pattern R6_FLOAT = Pattern.compile("\\\\(?:caption|label|ref)\\b|\\bfigure\\b");

    /** R8 多系列折线（≥2 系列且 X≥8）数值标注必须上下错开 */
    private static final Pattern YBAR = Pattern.compile("ybar");
    private static final Pattern ADDPLOT = Pattern.compile("\\\\addplot\\b");
    private static final Pattern ANCHOR_SOUTH = Pattern.compile("anchor\\s*=\\s*south");
    private static final Pattern ANCHOR_NORTH = Pattern.compile("anchor\\s*=\\s*north");

    /** R12 文本参数中的百分号必须转义（只扫文本参数花括号内，不扫注释行） */
    private static final Pattern R12_TEXT_PARAM =
            Pattern.compile("\\b(?:title|xlabel|ylabel|zlabel|legend(?:\\s+entries)?)\\s*=\\s*\\{([^{}]*)\\}");
    private static final Pattern R12_LEGEND_ENTRY = Pattern.compile("\\\\addlegendentry\\s*\\{([^{}]*)\\}");
    private static final Pattern R12_NODE_TEXT = Pattern.compile("\\\\node(?:\\[[^\\]]*\\])?[^{}]*\\{([^{}]*)\\}");
    private static final Pattern R12_UNESCAPED_PERCENT = Pattern.compile("(^|[^\\\\])%");

    private ChartCodeValidator() {
    }

    /**
     * 是否存在「两个系列坐标完全相同」。
     *
     * @param chartCode tikz 代码；传 rag_vector.content 亦可（只扫描 {@code \addplot} 片段）
     */
    public static boolean hasDuplicateSeries(String chartCode) {
        List<Set<String>> series = extractSeries(chartCode);
        for (int i = 0; i < series.size(); i++) {
            Set<String> current = series.get(i);
            if (current.size() < 2) {
                continue;   // 单点系列不足以构成「两条重合的线」
            }
            for (int j = i + 1; j < series.size(); j++) {
                if (current.equals(series.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 是否含 tikz 画布（{@code \begin{tikzpicture}}）。
     * <p>[语料治理] 用于提取阶段的<b>最小校验</b>：兜底提取逻辑过松时，模型对「你是谁」这类
     * 非图表提问的闲聊回复也会被「提取」成代码（history 27/28/30… 事故），
     * 这类内容不是图表代码，必须拦在落库与向量索引之前。</p>
     */
    public static boolean hasTikzStructure(String chartCode) {
        return chartCode != null && TIKZ_BEGIN.matcher(chartCode).find();
    }

    /**
     * 入库准入统一入口：返回命中的违规规则名列表，<b>空列表表示通过</b>。
     *
     * <p>扫描对象是「即将写入向量库的案例内容」；已被编译前预处理确定性兜底的规则
     * （R7 全角逗号、R10 直方图 ybar、R11 ymax 覆盖）<b>刻意不纳入</b>，避免误杀会被自动修复的案例。</p>
     *
     * <p>规则集合：{@code NO_TIKZ}（最基础）、R1、R2、R3、R5、R6、R8、R9、R12。</p>
     */
    public static List<String> violationsForIngest(String chartCode) {
        List<String> hits = new ArrayList<>();
        if (chartCode == null || chartCode.trim().isEmpty()) {
            return hits;    // 空内容由调用方的「非空」判定负责，此处不重复报违例
        }
        // 0. 最基础判据：必须含 tikz 画布。命中即返回——连画布都没有时，其余规则全部无意义
        if (!hasTikzStructure(chartCode)) {
            hits.add("NO_TIKZ");
            return hits;
        }
        // R1 图例不遮挡数据
        if (R1_LEGEND_POS.matcher(chartCode).find() || R1_LEGEND_TO_NAME.matcher(chartCode).find()) {
            hits.add("R1_LEGEND_POS");
        }
        // R2 轴外不写文字
        if (R2_NODE_BOUNDING_BOX.matcher(chartCode).find()) {
            hits.add("R2_NODE_OUTSIDE");
        }
        // R3 数值标注与数据点标记成对出现
        boolean hasMark = R3_MARK.matcher(chartCode).find();
        boolean hasNodesNearCoords = R3_NODES_NEAR_COORDS.matcher(chartCode).find();
        if ((hasMark && !hasNodesNearCoords)
                || R3_INVALID_KEY.matcher(chartCode).find()
                || (hasNodesNearCoords && (!FILL_NONE.matcher(chartCode).find() || !DRAW_NONE.matcher(chartCode).find()))) {
            hits.add("R3_NODES_PAIRED");
        }
        // R5 误差棒语法（取第一处 coordinates 块，与 violations.mjs 的 code.match 行为一致）
        if (R5_ERROR_BARS.matcher(chartCode).find() && !R5_PLUS_MINUS.matcher(chartCode).find()) {
            hits.add("R5_ERROR_BARS");
        }
        Matcher firstCoords = COORDINATES.matcher(chartCode);
        if (firstCoords.find() && R5_TRIPLE.matcher(firstCoords.group(1)).find()) {
            hits.add("R5_ERROR_BARS");
        }
        // R6 单图结构
        if (countMatches(TIKZ_BEGIN, chartCode) != 1 || R6_FLOAT.matcher(chartCode).find()) {
            hits.add("R6_SINGLE_TIKZ");
        }
        // R8 多系列折线/曲线（≥2 系列且 X≥8）标注必须上下错开；柱状图不在规则范围内
        boolean isBarChart = YBAR.matcher(chartCode).find();
        if (hasNodesNearCoords && !isBarChart
                && countMatches(ADDPLOT, chartCode) >= 2 && countXPoints(chartCode) >= 8
                && !(ANCHOR_SOUTH.matcher(chartCode).find() && ANCHOR_NORTH.matcher(chartCode).find())) {
            hits.add("R8_LABEL_STAGGER");
        }
        // R9 多系列数据必须互不相同（与 hasDuplicateSeries 同源）
        if (hasDuplicateSeries(chartCode)) {
            hits.add("R9_DUPLICATE_SERIES");
        }
        // R12 文本参数中的百分号必须转义
        if (hasUnescapedPercentInTextParams(chartCode)) {
            hits.add("R12_UNESCAPED_PERCENT");
        }
        return hits;
    }

    /** 按 {@code \addplot} 切段，逐段抽出坐标点集合（x|y，已去空白）。 */
    private static List<Set<String>> extractSeries(String chartCode) {
        List<Set<String>> result = new ArrayList<>();
        if (chartCode == null || chartCode.isEmpty()) {
            return result;
        }
        for (String segment : chartCode.split("\\\\addplot")) {
            Matcher coordinates = COORDINATES.matcher(segment);
            if (!coordinates.find()) {
                continue;
            }
            Set<String> points = new HashSet<>();
            Matcher point = POINT.matcher(coordinates.group(1));
            while (point.find()) {
                points.add(point.group(1).trim() + "|" + point.group(2).trim());
            }
            if (!points.isEmpty()) {
                result.add(points);
            }
        }
        return result;
    }

    /** 正则命中次数。 */
    private static int countMatches(Pattern pattern, String code) {
        Matcher matcher = pattern.matcher(code);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** 字符出现次数。 */
    private static int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * 估算 X 轴点数：优先取 {@code symbolic x coords={…}} 的分类数，否则取最长 {@code coordinates {…}}
     * 块中的坐标个数（对齐 violations.mjs 的 {@code countXPoints}）。
     */
    private static int countXPoints(String code) {
        Matcher symbolic = SYMBOLIC_COORDS.matcher(code);
        if (symbolic.find()) {
            int count = 0;
            for (String item : symbolic.group(1).split(",")) {
                if (!item.trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        }
        int max = 0;
        Matcher block = COORDINATES_GLOBAL.matcher(code);
        while (block.find()) {
            int count = countChar(block.group(1), '(');
            if (count > max) {
                max = count;
            }
        }
        return max;
    }

    /** 文本参数（title/xlabel/ylabel/legend/\addlegendentry/\node 文字）花括号内是否存在未转义的 %。 */
    private static boolean hasUnescapedPercentInTextParams(String code) {
        List<String> texts = new ArrayList<>();
        collect(R12_TEXT_PARAM, code, texts);
        collect(R12_LEGEND_ENTRY, code, texts);
        collect(R12_NODE_TEXT, code, texts);
        for (String text : texts) {
            if (R12_UNESCAPED_PERCENT.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** 把正则的所有第 1 捕获组依次放入 out（对齐 violations.mjs 的 collect）。 */
    private static void collect(Pattern pattern, String code, List<String> out) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
    }
}
