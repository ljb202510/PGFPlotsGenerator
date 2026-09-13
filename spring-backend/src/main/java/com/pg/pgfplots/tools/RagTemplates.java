package com.pg.pgfplots.tools;

import java.util.List;

/**
 * [RAG] 进阶图型模板库（批次1/A1 seed 数据源）：内容摘自 PromptTemplates【进阶图型模板】与【正例】段落。
 * <p>refId 固定 1..N，保证 rag_vector 的 uk_source_ref 幂等重建。</p>
 */
final class RagTemplates {

    private RagTemplates() {
    }

    record TemplateSeed(int refId, String title, String embedText, String content) {
    }

    private static final String BAR_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                ybar,
                title={各车间产量对比},
                ylabel={产量（台）},
                symbolic x coords={一车间,二车间,三车间,四车间,五车间},
                xtick=data,
                nodes near coords,
                every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south},
                legend style={at={(1.03,0.5)}, anchor=west}
            ]
            \\addplot coordinates {(一车间,4520) (二车间,6100) (三车间,3890) (四车间,5230) (五车间,2980)};
            \\end{axis}
            \\end{tikzpicture}""";

    private static final String LINE_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                width=11cm, height=6.5cm,
                title={2019-2024 年产量与销量},
                xlabel={年份}, ylabel={数量},
                legend style={at={(1.03,0.5)}, anchor=west}
            ]
            \\addplot[blue, mark=*, thick, nodes near coords, every node near coord/.append style={font=\\tiny, fill=none, draw=none, inner sep=1pt, anchor=south}] coordinates {(2019,320) (2020,410) (2021,455) (2022,520) (2023,610) (2024,690)};
            \\addlegendentry{产量}
            \\addplot[red, mark=square*, thick, nodes near coords, every node near coord/.append style={font=\\tiny, fill=none, draw=none, inner sep=1pt, anchor=north}] coordinates {(2019,300) (2020,392) (2021,440) (2022,505) (2023,590) (2024,665)};
            \\addlegendentry{销量}
            \\end{axis}
            \\end{tikzpicture}""";

    private static final String PIE_EXAMPLE = """
            \\begin{tikzpicture}
            \\pie[radius=3, text=legend]
                {38/品牌A, 27/品牌B, 20/品牌C, 15/其他}
            \\end{tikzpicture}""";

    private static final String SCATTER_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                only marks,
                mark=*, mark size=3pt,
                xlabel={身高（cm）}, ylabel={体重（kg）},
                nodes near coords, every node near coord/.append style={font=\\tiny, fill=none, draw=none, inner sep=1pt, anchor=south}
            ]
            \\addplot coordinates { (165,55) (170,62) (175,70) (180,78) (185,85) };
            \\end{axis}
            \\end{tikzpicture}""";

    private static final String STACKED_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                ybar stacked, width=10cm, height=6cm,
                symbolic x coords={华北,华东,华南,西部}, xtick=data,
                ymin=0, ymax=100, enlarge x limits=0.15,
                legend style={at={(1.03,0.5)}, anchor=west}
            ]
            \\addplot[fill=blue!50, nodes near coords, point meta=explicit symbolic, every node near coord/.append style={anchor=center, font=\\tiny, fill=none, draw=none}] coordinates {(华北,32)[32] (华东,28)[28] (华南,30)[30] (西部,35)[35]};
            \\addplot[fill=red!50, nodes near coords, point meta=explicit symbolic, every node near coord/.append style={anchor=center, font=\\tiny, fill=none, draw=none}] coordinates {(华北,26)[26] (华东,30)[30] (华南,25)[25] (西部,20)[20]};
            \\legend{线上,线下}
            \\end{axis}
            \\end{tikzpicture}""";

    private static final String DENSE_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                ybar, width=13cm, height=7cm,
                symbolic x coords={广东,江苏,山东,浙江,河南,四川,湖北,福建,湖南,上海}, xtick=data,
                x tick label style={rotate=45, anchor=east},
                ylabel={GDP（亿元）}, enlarge x limits=0.15,
                nodes near coords, every node near coord/.append style={anchor=south, font=\\scriptsize, fill=none, draw=none, inner sep=1pt}
            ]
            \\addplot[fill=blue!50] coordinates {(广东,135673)[13.6万] (江苏,128222)[12.8万] (山东,92069)[9.2万] (浙江,82553)[8.3万] (河南,61245)[6.1万] (四川,56749)[5.7万] (湖北,53734)[5.4万] (福建,51805)[5.2万] (湖南,48670)[4.9万] (上海,44652)[4.5万]};
            \\end{axis}
            \\end{tikzpicture}""";

    private static final String ERROR_BAR_EXAMPLE = """
            \\begin{tikzpicture}
            \\begin{axis}[
                ybar, width=10cm, height=6cm,
                symbolic x coords={方案A,方案B,方案C,方案D}, xtick=data,
                ylabel={产率（%）}, enlarge x limits=0.15,
                nodes near coords, every node near coord/.append style={font=\\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}
            ]
            \\addplot[fill=blue!50, error bars/.cd, y dir=both, y explicit]
              coordinates {(方案A,568)+-(0,35) (方案B,602)+-(0,22) (方案C,585)+-(0,30) (方案D,611)+-(0,28)};
            \\end{axis}
            \\end{tikzpicture}""";

    static final List<TemplateSeed> SEEDS = List.of(
            new TemplateSeed(1, "柱状图（ybar 数值标注）",
                    "画一个柱状图：ybar 分组柱状图对比各类别数值，nodes near coords 数值标注，图例外置，symbolic x coords 半角逗号",
                    "需求：画一个各车间产量对比的柱状图\n代码：\n" + BAR_EXAMPLE),
            new TemplateSeed(2, "折线图（多系列标注错开）",
                    "画一个折线图：addplot 折线展示数据随时间的趋势变化，多系列 nodes near coords 上下侧 anchor=south/north 错开防压盖",
                    "需求：画一个双系列折线图并标注数值\n代码：\n" + LINE_EXAMPLE),
            new TemplateSeed(3, "饼图（pgf-pie）",
                    "画一个饼图：pgf-pie 宏包 \\pie 命令展示各部分占比份额，图例标签",
                    "需求：画一个市场份额占比饼图\n代码：\n" + PIE_EXAMPLE),
            new TemplateSeed(4, "散点图（only marks）",
                    "画一个散点图：only marks + mark=* 展示两组数据的分布关系，禁止 scatter/scatter src=explicit（会覆盖数值标注）",
                    "需求：画一个身高体重关系散点图\n代码：\n" + SCATTER_EXAMPLE),
            new TemplateSeed(5, "堆叠柱状图（ybar stacked）",
                    "画一个堆叠柱状图：ybar stacked 展示各分类多层构成，point meta=explicit symbolic + (x,y)[label] 方括号标注各层原始值，百分比堆叠必须 ymin=0, ymax=100",
                    "需求：画一个各区域线上/线下销售额堆叠柱状图\n代码：\n" + STACKED_EXAMPLE),
            new TemplateSeed(6, "密集柱状图数值缩写",
                    "画一个密集柱状图：分类很多或数值很长时用 point meta=explicit symbolic 缩写 [label] 到 2-4 字符，y 值仍写真实值，全部点在同一 addplot 内统一 anchor=south",
                    "需求：画一个各省 GDP 对比的密集柱状图（长数值标注缩写）\n代码：\n" + DENSE_EXAMPLE),
            new TemplateSeed(7, "误差棒图（error bars）",
                    "画一个带误差棒的柱状图：error bars/.cd y dir=both y explicit + 坐标 +- 误差值写法，展示实验数据波动范围",
                    "需求：画一个带误差棒的实验数据对比柱状图\n代码：\n" + ERROR_BAR_EXAMPLE)
    );
}
