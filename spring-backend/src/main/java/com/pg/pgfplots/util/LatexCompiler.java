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

    /** 预处理：全角逗号归一、截取文档主体、行级清除脚手架、ybar 边距兜底。 */
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
            return fixYbarEnlargeLimits(chartCode);
        } catch (Exception e) {
            return originalCode;
        }
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
\\usepackage{pgfplots}
\\usepackage{pgf-pie} % 饼图：AI 代码可直接使用 \\pie
\\pgfplotsset{compat=1.18}
\\usepgfplotslibrary{fillbetween}  % 面积图 / 堆叠面积图 / \\closedcycle 填充路径
% 注：error bars 是 pgfplots 内置功能（在核心 pgfplots.errorbars.code.tex），不需要单独 \\usepgfplotslibrary 加载
\\usepackage{amsmath}
\\usepackage{amssymb}
\\usepackage{xcolor}[dvipsnames,svgnames]  % 加载标准色名表（steelblue/teal/orange/coral 等），避免 AI 用预定义色名时报 Undefined color

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
