package com.pg.pgfplots.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * LaTeX 处理工具，对应 Node {@code routes/compile.js} 中的
 * {@code validateLatexCode} / {@code preprocessLatexCode} / {@code createChineseLatexDocument}
 * / {@code compileLatexWithXeLaTeX} / {@code safeCleanup}。
 */
public final class LatexCompiler {

    /** 危险序列清单（阻断可执行命令 / 读写本地文件 / 加载未知宏包） */
    private static final List<Dangerous> DANGEROUS = List.of(
            new Dangerous("\\\\write18", "\\write18（执行系统命令）"),
            new Dangerous("\\\\shellescape", "\\shellescape（shell 转义）"),
            new Dangerous("\\\\openin", "\\openin（打开外部文件）"),
            new Dangerous("\\\\input", "\\input（读取外部文件）"),
            new Dangerous("\\\\include", "\\include（读取外部文件）"),
            new Dangerous("\\\\read", "\\read（读取外部文件）"),
            new Dangerous("\\\\includegraphics", "\\includegraphics（引用外部图片）"),
            new Dangerous("\\\\usepackage", "\\usepackage（加载宏包）"),
            new Dangerous("\\\\RequirePackage", "\\RequirePackage（加载宏包）"),
            new Dangerous("\\\\lstinputlisting", "\\lstinputlisting（读取外部文件）"),
            new Dangerous("\\\\verbatiminput", "\\verbatiminput（读取外部文件）"));

    private static final Pattern DOC_CLASS =
            Pattern.compile("\\\\documentclass(?:\\[[^\\]]*\\])?\\{[^}]*\\}.*$", Pattern.MULTILINE);
    private static final Pattern USE_PACKAGE =
            Pattern.compile("\\\\usepackage(?:\\[[^\\]]*\\])?\\{[^}]*\\}.*$", Pattern.MULTILINE);
    private static final Pattern SYMBOLIC =
            Pattern.compile("symbolic\\s+x\\s+coords=\\{([^}]+)\\}");
    private static final Pattern ABS_LIMITS =
            Pattern.compile("enlarge\\s+x\\s+limits=\\{\\s*abs=[^}]*\\}");
    private static final Pattern RATIO_LIMITS =
            Pattern.compile("enlarge\\s+x\\s+limits=\\s*0\\.(\\d+)");
    private static final Pattern HAS_LIMITS = Pattern.compile("enlarge\\s+x\\s+limits");
    private static final Pattern YBAR = Pattern.compile("\\bybar\\b");

    private static final int MAX_CODE_LENGTH = 50000;

    private LatexCompiler() {
    }

    /** 编译前安全校验（使用默认长度上限）。 */
    public static Validation validate(String code) {
        return validate(code, MAX_CODE_LENGTH);
    }

    /** 编译前安全校验（批次2/T2：长度上限可由 app.latex.max-code-length 配置驱动）。 */
    public static Validation validate(String code, int maxCodeLength) {
        if (code == null || code.isEmpty()) {
            return new Validation(false, "图表代码为空，无法编译");
        }
        if (code.length() > maxCodeLength) {
            return new Validation(false, "图表代码过长（超过 " + maxCodeLength + " 字符），请重新生成后再试");
        }
        for (Dangerous item : DANGEROUS) {
            if (Pattern.compile(item.pattern()).matcher(code).find()) {
                return new Validation(false, "检测到不安全的 LaTeX 指令 " + item.desc() + "，已阻止编译");
            }
        }
        return new Validation(true, "");
    }

    /** 预处理：全角逗号归一、截取文档主体、行级清除脚手架、非法 color 键剔除、at 坐标补花括号、空 axis 剔除、直方图补 ybar、ymax 覆盖不足修正、ybar 边距兜底。 */
    public static String preprocess(String originalCode) {
        try {
            if (originalCode == null || originalCode.trim().isEmpty()) {
                return originalCode;
            }

            String code = originalCode.replace("，", ",");

            String beginDoc = "\\begin{document}";
            String endDoc = "\\end{document}";
            int docStart = code.indexOf(beginDoc);
            int docEnd = code.indexOf(endDoc);
            if (docStart != -1 && docEnd != -1 && docEnd > docStart) {
                code = code.substring(docStart + beginDoc.length(), docEnd);
            }

            String cleaned = code
                    .replaceAll(DOC_CLASS.pattern(), "")
                    .replaceAll(USE_PACKAGE.pattern(), "")
                    .replace(beginDoc, "")
                    .replace(endDoc, "")
                    .trim();

            String chartCode = cleaned.isEmpty() ? originalCode : cleaned;
            return fixYbarEnlargeLimits(stripEmptyAxes(addYbarForHistogram(
                    ensureYmaxCoversData(braceAtCoordinates(dropInvalidColorKey(chartCode))))));
        } catch (Exception e) {
            return originalCode;
        }
    }

    /** [v1.4] axis 环境的起始（含可选参数区）与结束标记，仅用于剔除空 axis */
    private static final Pattern AXIS_BEGIN = Pattern.compile("\\\\begin\\{axis\\}\\s*(\\[[\\s\\S]*?\\])?");
    private static final Pattern AXIS_END = Pattern.compile("\\\\end\\{axis\\}");

    /**
     * [v1.4] 剔除「没有数据的空 axis」。
     * <p>判据取最保守的一种：整段代码一个 {@code \addplot} 都没有。此时 axis 没有任何可绘制内容，
     * 只会在图中多画一个空坐标系（表现为一个空方框，pgf-pie 等纯 TikZ 图元全部落在它外面）。</p>
     * <p>只要出现过 {@code \addplot}，一律原样返回，绝不误伤正常图。</p>
     */
    private static String stripEmptyAxes(String code) {
        if (!code.contains("\\begin{axis}") || code.contains("\\addplot")) {
            return code;
        }
        return AXIS_END.matcher(AXIS_BEGIN.matcher(code).replaceAll("")).replaceAll("");
    }

    /** [v1.5] 主观图型关键词：出现这些词说明作者意图是柱状分布图，而不是面积图（判据刻意收窄，避免误伤真面积图） */
    private static final Pattern HISTOGRAM_HINT = Pattern.compile("直方图|频数分布|分布图");
    /** [v1.5] {@code \begin{axis}} 及其后紧邻的选项区起始（插入点） */
    private static final Pattern AXIS_OPTION_START = Pattern.compile("\\\\begin\\{axis\\}\\s*\\[");
    private static final Pattern HAS_YBAR = Pattern.compile("\\bybar\\b");

    /**
     * [v1.5] 直方图缺 {@code ybar} 时补上。
     * <p>真实事故：模型写出了柱体填充色（{@code \addplot[fill=blue!70, draw=black]}），却漏写 axis 的
     * {@code ybar}；pgfplots 于是把填充色用在折线上闭合成多边形，渲染成「面积覆盖图」而不是柱状图。</p>
     * <p>判据取最保守的一种：仅当代码出现「直方图 / 频数分布 / 分布图」且 axis 选项里没有 ybar 时才补，
     * 真正的面积图（标题不含这些词）一律不动。</p>
     */
    private static String addYbarForHistogram(String code) {
        if (!code.contains("\\addplot")
                || HAS_YBAR.matcher(code).find()
                || !HISTOGRAM_HINT.matcher(code).find()) {
            return code;
        }
        Matcher axis = AXIS_OPTION_START.matcher(code);
        if (!axis.find()) {
            return code;
        }
        return code.substring(0, axis.end()) + "ybar, " + code.substring(axis.end());
    }

    /** [v1.6] axis 选项里显式写的 ymax=<数值> */
    private static final Pattern YMAX_OPT = Pattern.compile("ymax\\s*=\\s*(-?\\d+(?:\\.\\d+)?)");
    /** [v1.6] coordinates {...} 块 */
    private static final Pattern COORDINATES_BLOCK = Pattern.compile("coordinates\\s*\\{([^}]*)\\}");
    /** [v1.6] 坐标点 (x,y) 的 y 分量（x/y 内部不含括号） */
    private static final Pattern COORD_Y = Pattern.compile("\\([^(),]+,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)");

    /**
     * [v1.6] ymax 必须覆盖全部数据。
     * <p>真实事故：{@code ymax=40000} 而北京 = 40184，柱子被轴顶裁掉、顶部标注不可见。</p>
     * <p>只在「ymax 小于数据最大值」时修正（即真的发生了裁切），抬到 最大值 × 1.1 以留出约 10% 余量；
     * 未显式写 ymax（pgfplots 会自动留余量）或 ymax 已够大的图一律不动 —— 不做无必要的"改进"。</p>
     */
    private static String ensureYmaxCoversData(String code) {
        Matcher ymax = YMAX_OPT.matcher(code);
        if (!ymax.find()) {
            return code;
        }
        double maxY = maxYValue(code);
        if (maxY <= 0 || Double.parseDouble(ymax.group(1)) >= maxY) {
            return code;
        }
        long raised = (long) Math.ceil(maxY * 1.1);
        return code.substring(0, ymax.start()) + "ymax=" + raised + code.substring(ymax.end());
    }

    /** 扫描所有 {@code coordinates} 块取最大 y 值；无可用数值返回 0。 */
    private static double maxYValue(String code) {
        double max = 0;
        Matcher blocks = COORDINATES_BLOCK.matcher(code);
        while (blocks.find()) {
            Matcher points = COORD_Y.matcher(blocks.group(1));
            while (points.find()) {
                try {
                    max = Math.max(max, Double.parseDouble(points.group(1)));
                } catch (NumberFormatException ignored) {
                    // 非数值 y（如符号标签）跳过，不影响其它点
                }
            }
        }
        return max;
    }

    /** [v1.7] 误用的 {@code color={<逗号列表>}}：pgfplots 的 color 只接受单一颜色（连同该行一起删除） */
    private static final Pattern INVALID_COLOR_KEY =
            Pattern.compile("[ \\t]*color\\s*=\\s*\\{[^{}]*,[^{}]*\\}[ \\t]*\\r?\\n?");
    /** [v1.7] 未加花括号的 at 坐标：{@code at=(1.03,0.5)} */
    private static final Pattern UNBRACED_AT = Pattern.compile("\\bat\\s*=\\s*(\\(\\s*[^()]*,[^()]*\\))");

    /**
     * [v1.7] 删除误用的 {@code color={blue, red, green, orange, purple}}。
     * <p>真实事故：模型把色环当成了 color 的值，xcolor 会把整串当成一个颜色名，抛
     * {@code Undefined color 'blue, red, green, orange, purple'} —— 每个柱体报一次，
     * 刷屏 50+ 条错误并把编译拖到 30s 超时。</p>
     * <p>只删「花了括号且内含逗号」的 color 键；{@code color=blue} / {@code color={blue}} 不动。</p>
     * <p><b>必须连同整行一起删除</b>：若只删键本身，会留下一行只有空白的文本 —— 而 TeX 会丢弃行尾空白，
     * 使「只有空白的行」等价于空行（{@code \par}），在 axis 选项区里会中断选项解析并抛
     * {@code Paragraph ended before \pgfplots@@environment@axis was complete}（本规则首版就踩了这个坑）。
     * 删除后若留下空键位（如 {@code ybar, ,}），pgfkeys 会忽略空键，无副作用。</p>
     */
    private static String dropInvalidColorKey(String code) {
        return INVALID_COLOR_KEY.matcher(code).replaceAll("");
    }

    /**
     * [v1.7] 给未加花括号的 at 坐标补上花括号：{@code at=(1.03,0.5)} → {@code at={(1.03,0.5)}}。
     * <p>真实事故：pgfkeys 用逗号分隔键，未加花括号的 {@code at=(1.03,0.5)} 会被切成
     * {@code at=(1.03} 与 {@code 0.5)} 两个键，抛 {@code Runaway argument} 并读到段落结束，
     * 是一类会直接中断编译的致命错误。已加花括号的写法不会被匹配，故不会重复处理。</p>
     */
    private static String braceAtCoordinates(String code) {
        return UNBRACED_AT.matcher(code).replaceAll("at={$1}");
    }

    /** P3：N≥7 时把任意 enlarge x limits 统一为比例 0.15。 */
    private static String fixYbarEnlargeLimits(String code) {
        Matcher symMatch = SYMBOLIC.matcher(code);
        if (!symMatch.find()) {
            return code;
        }
        long n = Arrays.stream(symMatch.group(1).split(",")).map(String::trim).filter(s -> !s.isEmpty()).count();
        if (n <= 6) {
            return code;
        }

        String out = ABS_LIMITS.matcher(code).replaceAll("enlarge x limits=0.15");

        Matcher ratio = RATIO_LIMITS.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (ratio.find()) {
            double value = Double.parseDouble("0." + ratio.group(1));
            ratio.appendReplacement(sb, Matcher.quoteReplacement(
                    value < 0.15 ? "enlarge x limits=0.15" : ratio.group(0)));
        }
        ratio.appendTail(sb);
        out = sb.toString();

        if (!HAS_LIMITS.matcher(out).find() && YBAR.matcher(out).find()) {
            Matcher ybar = YBAR.matcher(out);
            out = ybar.replaceFirst(Matcher.quoteReplacement("ybar, enlarge x limits=0.15"));
        }
        return out;
    }

    /** 文档外壳模板，{@code __CHART_CODE__} 处替换为图表代码。 */
    private static final String DOC_TEMPLATE = """
\\documentclass[border=5pt]{standalone}
% 命名色表必须在 pgfplots 之前声明：pgfplots 内部会先加载 xcolor，
% 之后再 \\usepackage[dvipsnames,svgnames]{xcolor} 会触发 Option clash
\\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}
\\usepackage{pgfplots}
\\usepackage{pgf-pie} % 饼图：AI 代码可直接使用 \\pie
\\pgfplotsset{compat=1.18}
\\usepgfplotslibrary{fillbetween}  % 面积图 / 堆叠面积图 / \\closedcycle 填充路径
% 注：error bars 是 pgfplots 内置功能（在核心 pgfplots.errorbars.code.tex），不需要单独 \\usepgfplotslibrary 加载
\\usepackage{amsmath}
\\usepackage{amssymb}

% 支持中文
\\usepackage{fontspec}
\\usepackage{xeCJK}
\\setCJKmainfont{SimSun}
\\setmainfont{Times New Roman}

\\begin{document}

__CHART_CODE__

\\end{document}""";

    /** 套用支持中文的 standalone 文档外壳。 */
    public static String buildDocument(String chartCode) {
        return DOC_TEMPLATE.replace("__CHART_CODE__", chartCode == null ? "" : chartCode);
    }

    /** 调用 xelatex 编译，30s 超时；以 PDF 是否存在判定成败。 */
    public static CompileResult run(Path texFile, Path outputDir, String executable, long timeoutMs) {
        String fileName = stripExtension(texFile.getFileName().toString());
        Path pdfPath = outputDir.resolve(fileName + ".pdf");
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    executable,
                    "-interaction=nonstopmode",
                    "-output-directory=" + outputDir,
                    texFile.toString());
            builder.directory(texFile.getParent().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // 忽略读取异常
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            reader.join(2000);
            return new CompileResult(Files.exists(pdfPath), output.toString());
        } catch (Exception e) {
            // 记录执行异常本身（如「找不到 xelatex」）：否则 output 为空，
            // 调用方拿到的失败信息整体空白，既无任务 error 也无 api_log.call_error 可读
            output.append("xelatex 执行异常: ").append(e.getMessage()).append('\n');
            return new CompileResult(Files.exists(pdfPath), output.toString());
        }
    }

    /** 安全清理目录（逐个删文件后删目录，忽略错误）。 */
    public static void safeCleanup(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 忽略删除错误
                }
            });
        } catch (IOException ignored) {
            // 忽略
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // 忽略目录删除错误
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 校验结果 */
    public record Validation(boolean valid, String message) {
    }

    /** 编译结果 */
    public record CompileResult(boolean success, String output) {
    }

    private record Dangerous(String pattern, String desc) {
    }
}
