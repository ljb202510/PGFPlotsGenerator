# 编译问题 / 图表质量问题沉淀

> 面向 **AI 图表生成 → PGFPlots 代码 → XeLaTeX 编译 PDF** 全链路的历史问题总结。
> 仅覆盖图表生成链路（不含 Maven / ESLint / 部署等工程构建层）。
> 数据来源：`docs/log.md`、`docs/test/manual-test-cases.md`（§7.1、§9.5）、`docs/test/manual-test-cases-checklist.md`（§D、§E）。
> 汇总日期：2026-09-15；2026-09-18 随提示词 `v1.5-coord-color-note`（R1–R16）与 `preprocess` 12 条规则同步口径。状态图例：✅ 已修复　🟡 已兜底（编译前预处理）　❌ 已放弃　⏳ 仍待办

---

## 一、编译问题（LaTeX / XeLaTeX 编译失败）

| # | 问题现象 | 根因 | 处置 / 修复 | 状态 |
|---|----------|------|------------|:---:|
| C1 | AI 返回完整文档（`\documentclass`/`\usepackage`/`\begin{document}`）被编译外壳再包裹，直接编译失败 | AI 违规输出文档脚手架 | 提示词【输出边界】禁止输出脚手架；`preprocessLatexCode` 无条件清理；外壳预置 `pgf-pie` | ✅ |
| C2 | 整段 `tikzpicture` 挤成一行，XeLaTeX 直接报错（`bar_dense`） | 模型把换行输出成**字面 `\n`**（反斜杠+n 两个字符）而非真实换行 | `ChartCodeExtractor.normalizeEscapes`：lookahead/lookbehind 双重保护还原转义；接入 extract / extractFencedBlock / StructuredOutputParser 三条出口 | ✅ |
| C3 | `symbolic x coords` 用全角中文逗号 `，`，整串被当作单个分类 → 所有数据堆在一起 / 编译致命失败 | 中文逗号不是 pgfplots 分隔符 | `preprocessLatexCode` 兜底 `code.replace(/，/g, ',')`，一次性消除用例 17/19 两个致命失败；提示词强约束英文半角逗号（R7） | ✅ |
| C4 | 编译 `TeX capacity exceeded [input stack size]` 递归溢出（用例 03 饼图偶发） | AI 偶发在 `\pie` 内叠加 `\node at (axis description cs:…)`，饼图无 axis 坐标系统 → 递归溢出 | 重跑即过；提示词饼图专项（禁止 axis 包裹），非确定性修复 | 🟡 |
| C5 | `color={含逗号}` 时每柱报一次错、刷屏 50+ 条，编译拖到 30s 超时 | `color=` 只接受单色，逗号列表被 xcolor 当成一个颜色名 | 预处理 `dropInvalidColorKey` **连同整行删除**（只删键会留空行，TeX 把空行当 `\par` 在 axis 选项区断解析，产生 100 个错） | ✅ |
| C6 | `Runaway argument` 致命错误 | `at=(x,y)` 未加花括号，逗号被 pgfkeys 当键分隔符 | 预处理 `braceAtCoordinates`：`at=(x,y)` → `at={(x,y)}` | ✅ |
| C7 | 饼图被 axis 包裹多画一个 0–1 空坐标框，图元全落在框外 | AI 把饼图画在 axis 环境内 | 预处理 `stripEmptyAxes`：全图无 `\addplot` 时删除 axis 环境 | ✅ |
| C8 | 直方图漏写 `ybar` 渲染成面积覆盖图 | 模型只写 `fill=` 却漏 `ybar` | 预处理 `addYbarForHistogram`：含「直方图/频数分布/分布图」且无 `ybar` 时插入 `ybar,`；提示词 R10 | ✅ |
| C9 | 面积图 / 误差棒图渲染空白 | 编译外壳未加载 pgfplots 子库 `fillbetween` / `errorbars` | 外壳 `\usepgfplotslibrary{fillbetween}` + `\usepgfplotslibrary{errorbars}` | ✅ |
| C10 | `SteelBlue/MidnightBlue/TealBlue` 报 `Undefined color`、`Missing character ... in font nullfont` | 文档外壳 `\usepackage{xcolor}[dvipsnames,svgnames]` 选项位置错误 | 改为在 `\usepackage{pgfplots}` 之前 `\PassOptionsToPackage{dvipsnames,svgnames}{xcolor}` | ✅ |
| C11 | 用户只见「编译失败」却无任何线索（error / call_error 双双空白） | `LatexCompiler.run` 的 `catch (Exception e)` 把 `ProcessBuilder` 异常（如找不到 xelatex）直接吞掉，`output` 为空 | catch 中追加 `xelatex 执行异常: <msg>`，仅补错误信息不改编译/超时/清理逻辑 | ✅ |
| C12 | 编译错误 Toast 只展示前 300 字符，真实 LaTeX 报错被截断看不到；数千字符塞进 `ElMessage.error` 又触发全屏红字且无法关闭 | 错误展示两极化：过短或过长 | 前端改截断到 200 字符 + `showClose` + `duration:5000`；失败样本/日志留档 `data/storage/debug/hist{id}_{时间戳}.tex(.log)` | ✅ |

---

## 二、图表质量问题（渲染效果）

| # | 问题现象 | 根因 | 处置 / 修复 | 状态 |
|---|----------|------|------------|:---:|
| Q1 | 图例撞数据主体（递减数据必撞左上图例区） | 图例位置固定预定义角（north west/south east 等），与数据形状无关 | 提示词 R1：禁止 `legend pos=north west/north east` 等压数据区写法 | ✅ |
| Q2 | 轴外 `\node` 锚点不可靠，文字漂出 axis | 用 `current bounding box` / `axis description cs` 相对坐标时，frame/box 阶段尺寸不一致 | 提示词 R2：禁止 axis 外任何文字；数据来源等一律用标准结构 | ✅ |
| Q3 | 数值标注带白底黑框盖住数据点；偶发完全不显示标注 | 数值标注用了旧无效键 `nodes near coords style=` | 改正确键 `every node near coord/.append style={font=\scriptsize, fill=none, draw=none, inner sep=1pt, anchor=south}`；R3 强制「有 mark 必配标注」 | ✅ |
| Q4 | 两条折线数值标注互相压盖并贴住线条/数据点（用例 06） | 多系列同点标注重叠，Y 值接近/交叉处尤甚 | 提示词 R8：多系列按系列错开锚点 `anchor=south/north`、字号 `\tiny/\scriptsize` | ✅ |
| Q5 | 单系列密集点标注全部 `anchor=south` 堆在上方互相压盖（用例 16，X≥8） | R3 只给「统一上对齐」，未覆盖单系列密集点 | R3 补「单系列 X≥8 按与相邻点 y 值关系交替 `anchor=south/north`」 | ✅ |
| Q6 | 14 根密集柱柱顶长数字互相压盖（用例 25） | AI 直接写完整长数字（135673）超出柱间距；「密集柱须缩写」规则 qwen 不遵守 | 🟡 `abbreviateDenseYbarLabels`：坐标追加 `[a.b万]` 显示标签（y 保留真实值保柱高）+ `point meta=explicit symbolic` + `\tiny` + `inner sep=0pt`（缩写后仍剩约 1pt 物理下限碰触，待肉眼终确认） | 🟡 |
| Q7 | X 轴长中文分类标签互相压盖（用例 30/31/27） | AI 未写 `x tick label style`（31）或写了 `rotate=0`（30），长标签无旋转 | 🟡 `fixXTickLabels`：注入 `x tick label style={font=\scriptsize, rotate=30, anchor=east}`；非零旋转尊重作者、`rotate=0` 视为事故整体覆盖、未写则注入、短分类（<6）不动 | 🟡 |
| Q8 | 负值季度柱「不显示」（2023Q2/2023Q4/2024Q3） | AI 把正/负利润拆成两个 `\addplot`，x 不全对应 → pgfplots 当多系列并排分配柱位，负值柱被推到错位槽位挤出可视区 | 🟡 `mergeSplitYbarAddplots`：仅合并「同一数据集被拆」（ybar 非 stacked、≥2 addplot、x 两两互不重叠）；真多系列 x 重叠不误合并 | 🟡 |
| Q9 | 面积图 / 误差棒图 / 堆叠面积图渲染空白 | AI 误用风格键、坐标三元组非标、缺子库；`stack plots=y`+`area plot` 需 fillbetween | 补正例模板 + 加载子库（见 C9）；堆叠面积图 pgfplots 无原生支持且少用 → **放弃** | ❌（仅堆叠面积图） |
| Q10 | 单数据点 / 负值柱左右边缘被画布截断只剩一半 | `enlarge x limits=0.08` 太小，分段点少扩展不足 | ybar 边距规则：禁止 `abs`、统一比例 `enlarge x limits=0.15`；P3 对 N≥7 统一替换 | ✅ |
| Q11 | 大数值柱值/刻度单位混用（元/亿元），出现异常巨大/微小刻度 | Y 轴单位与数据量级不一致 | 提示词 R4：数值与坐标轴量纲一致；用例 08 明确「同单位量级」；预处理/人工双保障 | ✅ |
| Q12 | 递减序列所有数据堆在一起，一根柱撑满画布 | `symbolic x coords` 全角逗号把整串当单个分类 | 同 C3 全角逗号兜底 | ✅ |
| Q13 | `ybar stacked` 编译成功但渲染为空白 | axis 级 `nodes near coords` 与多层 `ybar stacked` 语法-渲染路径冲突 | 补堆叠柱模板（`point meta=explicit symbolic` + `(x,y)[原始值]` + `ymin=0,ymax=100` 防裁剪） | ✅ |
| Q14 | 散点图渲染出点但无任何数值标注 | `scatter src=explicit` 要求显式第三维散点数据，AI 未提供 | 补散点模板：不用 `scatter`/`scatter src=explicit`，只用 `only marks, mark=*` + axis 级 `nodes near coords` | ✅ |
| Q15 | 生成图表形状空白（只有标题、无柱形/折线） | 改提示词引入渲染规则后出现的新 bug 类 | 编译前预处理兜底 + 规则回归（R1–R16）；遇空白按单位/结构排查（见下节防线） | 🟡 |

---

## 三、沉淀的防线（按层归纳）

**1. 提示词渲染规则（R1–R16，当前版本 `v1.5-coord-color-note`）**——约束 AI 输出：
R1 图例不遮挡 / R2 轴外无文字 / R3 数据点与数值标注成对 / R4 数值与坐标轴量纲一致 / R5 无误差列不启 error bars / R6 单 tikzpicture、禁浮动体与交叉引用 / R7 symbolic x 用半角逗号 / R8 多系列标注错开 / R9 多系列坐标互不相同 / R10 直方图必须 ybar、禁填充面积 / R11 ymax ≥ 最大值并留约 10% 余量 / R12 文本参数里的 `%` 必须写 `\%` / R13 xbar 坐标序为（数值, 分类）且配 symbolic y coords / R14 正负值柱必须写同一 `\addplot` / R15 数据来源注记写在 `\end{axis}` 之后用 `current bounding box`（禁 `axis description cs`）/ R16 颜色名必须用 xcolor 已定义写法（驼峰基色 + `!` 混合，禁 CSS 小写色名）；另有饼图专项、输出边界、上下文独立、密集柱缩写等规则。

**2. 编译前确定性预处理（`LatexCompiler.preprocess`，12 条规则按序执行，模型写错也能出对图）**：
`fixHorizontalBarCoords`（R13 坐标序）/ `fixUndefinedColors`（R16 色名）/ `dropInvalidColorKey`（C5）/ `braceAtCoordinates`（C6）/ `ensureYmaxCoversData`（R11）/ `addYbarForHistogram`（R10/C8）/ `stripEmptyAxes`（C7）/ `fixYbarEnlargeLimits`（Q10）/ `fixXTickLabels`（Q7）/ `mergeSplitYbarAddplots`（Q8）/ `abbreviateDenseYbarLabels`（Q6）/ `normalizeSourceNote`（R15 注记搬运）；入口处另有字面 `\n` 还原（`ChartCodeExtractor.normalizeEscapes`）与全角逗号替换（C3）。

**3. 编译链路加固**：
`ChartCodeExtractor.normalizeEscapes`（C2）、`validateLatexCode` 编译前拦截 `\write18`/`\input`/`\includegraphics`/`\usepackage`/超长代码等（安全 + 稳定性）、外壳预置 pgfplots/pgf-pie 及 `fillbetween`/`errorbars` 子库（C9）。

**4. 静态合规检测（`spring-backend/eval/violations.mjs`）**：R1/R2/R3/R5/R6/R7/R8/R9/R12 离线检测；刻意不做 R4/R10/R11（量纲需语义理解会误报；后两者已被预处理在编译阶段消除，检测恒零违例会虚高零违例率）；R13–R16（v1.5 新增）暂不在静态检测范围。

**5. 校验兜底（`ChartCodeValidator` / `Retriever`）**：重复系列不入 RAG 库；RAG 同题断链（文字相同 / 相似度≥0.98 剔除），防「照抄上一次错误代码」的自污染循环。

---

## 四、仍待办 / 已知取舍

- **Q6** 密集柱缩写后约 1pt 级碰触为字体物理下限，再减需去掉「万」后缀或降数字精度（取舍未做，待定）。
- **C4** 饼图 `\pie` 递归溢出仅靠提示词 + 重跑，无确定性预处理。
- 编译成功仍以「PDF 是否存在」判定，致命错误可能静默产出空白 PDF（与编译失败解耦未做）。
- `\%` 转义仅靠提示词规则，无预处理兜底。

---

## 附：测试通过率参考（2026-09-15）

- 手动/自动化回归（用例 01–32，17 放弃）：RAG 开启与关闭各 31/31 全部通过，编译链路稳定。
- 视觉类三类问题（X 轴标签重叠 / 密集数值标注重叠 / 拆 addplot 柱位错乱）均已有编译预处理兜底覆盖，且与 RAG 开关无关。
