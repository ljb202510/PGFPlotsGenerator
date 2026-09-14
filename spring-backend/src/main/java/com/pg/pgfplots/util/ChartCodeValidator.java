package com.pg.pgfplots.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图表代码质量校验（[v1.2] 对应提示词规则 R9：多系列数据必须互不相同）。
 * <p>用途：作为 RAG 入库前的准入门槛——把「两个 addplot 复用同一组坐标」这类明显错误挡在索引之外，
 * 避免错误案例被当作 few-shot 范例反复喂回模型（自我强化循环：下次同题请求直接照抄上一次的错误结果）。</p>
 * <p>刻意只做保守、可判定的检查：能判定的判不合格，判不准的一律放行，绝不误伤正常图。</p>
 */
public final class ChartCodeValidator {

    /** 每个 \addplot 片段内取第一处 coordinates {...} */
    private static final Pattern COORDINATES = Pattern.compile("coordinates\\s*\\{([^}]*)\\}");

    /** 坐标点 (x,y)；x / y 内部不含括号，兼容 (2019,120.6) 与 (一季度,45) */
    private static final Pattern POINT = Pattern.compile("\\(([^(),]+),([^()]+)\\)");

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
}
