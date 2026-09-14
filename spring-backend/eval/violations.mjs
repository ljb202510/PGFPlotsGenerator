/**
 * 静态违例检测（批次3/评估集 V2，零依赖）。
 *
 * 依据：util/PromptTemplates.java 的【图表渲染规则】R1-R12 做正则启发式检查。
 *       当前静态覆盖 R1-R3、R5-R9、R12；R4 / R10 / R11 见下方「刻意不做的规则」。
 * 定位：把「通过」细化为「通过且零违例」，用于量化 RAG / 提示词迭代的真实增量。
 *
 * 注意：
 * - 本模块是**启发式**检测，不追求零误报；命中即提示人工复核，不要当作硬性断言。
 * - R7 只检查 LaTeX 结构上下文（symbolic x coords / coordinates 花括号内）里的全角逗号；
 *   不对整份代码做全角逗号扫描，避免把中文标签里的正常标点（如「数据来源：统计局，2024」）
 *   误判为违例。
 * - R9 与 util/ChartCodeValidator.hasDuplicateSeries 同源（同一判定：多个 \addplot 的
 *   coordinates 集合去重后相同，忽略顺序与空白），保证「离线评估口径」与「RAG 入库准入门槛」
 *   是同一把尺子，不会出现「评估说合规、入库却被拒」的口径分裂。
 * - R12 只检文本参数（title / xlabel / ylabel / legend 系 / \node 文字）花括号内未转义的 %，
 *   不扫整份代码——注释行里的 % 是合法 LaTeX 注释，全局扫描会把正常代码误判为违例。
 *
 * 刻意不做的规则（避免产出自我欺骗的指标）：
 * - **R4（数值与坐标轴单位/量级一致）**：要求理解数据语义标度（如「万元 vs 元」「人 vs 万人」），
 *   正则会大量误报，产出不可信的评估数字。该项保留为人工评审。
 * - **R10（直方图必须 ybar）/ R11（ymax 必须覆盖数据最大值）**：二者已被
 *   LatexCompiler.preprocess 在编译前**确定性修复**（addYbarForHistogram / ensureYmaxCoversData）。
 *   模型原始输出里的 R10/R11 违例在编译阶段就消失了，静态检测它们会恒为零违例，
 *   让零违例率虚高、失去区分度。该口径由 preprocess 负责，这里刻意不重复检测。
 *
 * @param {string} chartCode 模型生成的 LaTeX 图表代码
 * @returns {Array<{rule: string, reason: string}>} 违例列表（无违例为空数组）
 */
export function detectViolations(chartCode) {
  const code = String(chartCode || '');
  if (!code.trim()) {
    return [];
  }
  const violations = [];
  const add = (rule, reason) => violations.push({ rule, reason });

  // ---------------- R1 图例不遮挡数据 ----------------
  if (/legend\s+pos\s*=\s*(?:north\s+west|north\s+east)/i.test(code)) {
    add('R1_LEGEND_POS', '使用了 legend pos=north west/north east，会遮挡数据（R1）');
  }
  if (/legend\s+to\s+name\s*=/i.test(code)) {
    add('R1_LEGEND_POS', '使用了 legend to name=… 暂存后另处引用（R1 禁止）');
  }

  // ---------------- R2 轴外不写文字 ----------------
  if (/\\node\s+at\s*\(\s*current\s+bounding\s+box/i.test(code)) {
    add('R2_NODE_OUTSIDE', '使用 \\node at (current bounding box.*)，注记被画到轴外/图外（R2）');
  }

  // ---------------- R3 数值标注与数据点标记成对出现 ----------------
  const hasMark = /\bmark\s*=/.test(code);
  const hasNodesNearCoords = /nodes\s+near\s+coords/.test(code);
  if (hasMark && !hasNodesNearCoords) {
    add('R3_NODES_PAIRED', '有 mark= 数据点标记但缺少 nodes near coords 数值标注（R3）');
  }
  if (/nodes\s+near\s+coords\s+style\s*=/.test(code)) {
    add('R3_NODES_PAIRED', '使用了无效键 nodes near coords style=（应写 every node near coord/.append style，R3）');
  }
  if (hasNodesNearCoords && (!/fill\s*=\s*none/.test(code) || !/draw\s*=\s*none/.test(code))) {
    add('R3_NODES_PAIRED', 'nodes near coords 缺少 fill=none / draw=none（默认白底黑框会盖住数据点，R3）');
  }

  // ---------------- R5 误差棒语法 ----------------
  const hasPlusMinus = /\+-/.test(code);
  const hasErrorBars = /error\s+bars/i.test(code);
  if (hasErrorBars && !hasPlusMinus) {
    add('R5_ERROR_BARS', '数据未提供误差列却启用了 error bars（R5 负例）');
  }
  const coordsBlock = code.match(/coordinates\s*\{([\s\S]*?)\}/);
  if (coordsBlock && /\(\s*[^(),]+\s*,\s*[^(),]+\s*,\s*[^(),]+\s*\)/.test(coordsBlock[1])) {
    add('R5_ERROR_BARS', '坐标使用了三元组 (x,y,err)，应写成 (x,y) +- (err_x,err_y)（R5）');
  }

  // ---------------- R6 单图结构 ----------------
  const tikzCount = (code.match(/\\begin\{tikzpicture\}/g) || []).length;
  if (tikzCount !== 1) {
    add('R6_SINGLE_TIKZ', `\\begin{tikzpicture} 出现 ${tikzCount} 次，应恰好 1 次（R6）`);
  }
  if (/\\(?:caption|label|ref)\b/.test(code) || /\bfigure\b/.test(code)) {
    add('R6_SINGLE_TIKZ', '出现 figure / \\caption / \\label / \\ref（R6 禁止）');
  }

  // ---------------- R7 分类坐标轴分隔符（只查结构上下文） ----------------
  const symCoords = code.match(/symbolic\s+x\s+coords\s*=\s*\{([^}]*)\}/);
  if (symCoords && symCoords[1].includes('，')) {
    add('R7_FULLWIDTH_COMMA', 'symbolic x coords 内使用全角逗号，会被 pgfplots 当作单个分类（R7）');
  }
  if (coordsBlock && coordsBlock[1].includes('，')) {
    add('R7_FULLWIDTH_COMMA', 'coordinates 花括号内使用全角逗号，会破坏 (x,y) 坐标解析（R7）');
  }

  // ---------------- R8 多系列折线/曲线（≥2 系列且 X≥8）标注必须错开 ----------------
  // 严格对齐 R8 规则文本：只针对「折线/曲线、≥2 条系列、X≥8 个点」。
  // 柱状图（ybar / ybar stacked）与单系列不在规则范围内——否则会把合规的堆叠柱图误判为违例。
  // 系列数用 `\addplot` 计数（`nodes near coords` 可能写在 axis 选项里被所有系列共用，不能当系列数），
  // 且必须排除 `nodes near coords align=…` 这类后缀写法造成的假计数。
  const isBarChart = /ybar/.test(code);
  const seriesCount = (code.match(/\\addplot\b/g) || []).length;
  const pointCount = countXPoints(code);
  if (hasNodesNearCoords && !isBarChart && seriesCount >= 2 && pointCount >= 8) {
    const hasSouth = /anchor\s*=\s*south/.test(code);
    const hasNorth = /anchor\s*=\s*north/.test(code);
    if (!(hasSouth && hasNorth)) {
      add('R8_LABEL_STAGGER', '多系列折线/曲线（≥2 系列且 X≥8）数值标注未做上下错开，需同时出现 anchor=south 与 anchor=north（R8）');
    }
  }

  // ---------------- R9 多系列数据必须互不相同 ----------------
  // 与 util/ChartCodeValidator.hasDuplicateSeries 同源（逐条对齐判定），避免评估口径与入库准入分裂。
  // 单点系列（<2 个点）不参与比较：不足以构成「两条重合的线」，判了就是误报。
  if (hasDuplicateSeries(extractSeriesPoints(code))) {
    add('R9_DUPLICATE_SERIES', '存在坐标集合完全相同的多个 \\addplot 系列，曲线将完全重合、图例形同虚设（R9）');
  }

  // ---------------- R10 / R11 刻意不做静态检测 ----------------
  // R10（直方图必须 ybar）与 R11（ymax ≥ 数据最大值）已被 LatexCompiler.preprocess 在编译前
  // 确定性修复；模型原始输出中的违例在编译阶段即被消除，静态检测恒为零违例，只会虚高指标。

  // ---------------- R12 文本参数中的百分号必须转义 ----------------
  // 裸 % 会注释掉整行剩余内容，吞掉花括号使选项括号失衡；服务端只会得到空白 PDF 且不报错。
  for (const text of extractTextParamValues(code)) {
    if (hasUnescapedPercent(text)) {
      add('R12_UNESCAPED_PERCENT', `文本参数中出现未转义的 %（应写成 \\%），会注释掉后续内容导致编译失败（R12）：${text.trim().slice(0, 40)}`);
      break;
    }
  }

  return violations;
}

/**
 * 估算 X 轴点数：优先取 `symbolic x coords={…}` 的分类数，否则取最长 `coordinates {…}` 块中的坐标个数。
 * @param {string} code LaTeX 代码
 * @returns {number} X 轴点数（无法判断时为 0）
 */
function countXPoints(code) {
  const symbolic = code.match(/symbolic\s+x\s+coords\s*=\s*\{([^}]*)\}/);
  if (symbolic) {
    return symbolic[1].split(',').filter((s) => s.trim()).length;
  }
  let max = 0;
  const blockRe = /coordinates\s*\{([\s\S]*?)\}/g;
  let m = blockRe.exec(code);
  while (m !== null) {
    const n = (m[1].match(/\(/g) || []).length;
    if (n > max) {
      max = n;
    }
    m = blockRe.exec(code);
  }
  return max;
}

/**
 * 抽取每个 `\addplot` 片段里的坐标点集合（与 ChartCodeValidator.extractSeries 同源）。
 * 按 `\addplot` 切段，每段取第一处 `coordinates {…}`，点格式 `x|y`（已去空白）。
 * @param {string} code LaTeX 代码
 * @returns {Array<Set<string>>} 各系列的坐标点集合（无坐标的段不参与）
 */
function extractSeriesPoints(code) {
  const result = [];
  for (const segment of code.split('\\addplot')) {
    const block = segment.match(/coordinates\s*\{([^}]*)\}/);
    if (!block) {
      continue;
    }
    const points = new Set();
    const pointRe = /\(([^(),]+),([^()]+)\)/g;
    let m = pointRe.exec(block[1]);
    while (m !== null) {
      points.add(`${m[1].trim()}|${m[2].trim()}`);
      m = pointRe.exec(block[1]);
    }
    if (points.size > 0) {
      result.push(points);
    }
  }
  return result;
}

/**
 * 是否存在「两个系列坐标完全相同」（判定与 ChartCodeValidator.hasDuplicateSeries 逐条对齐）。
 * @param {Array<Set<string>>} series 各系列坐标集合
 * @returns {boolean}
 */
function hasDuplicateSeries(series) {
  for (let i = 0; i < series.length; i++) {
    const current = series[i];
    if (current.size < 2) {
      continue;
    }
    for (let j = i + 1; j < series.length; j++) {
      if (samePointSet(current, series[j])) {
        return true;
      }
    }
  }
  return false;
}

/** 两个点集合是否相等（顺序无关）。 */
function samePointSet(a, b) {
  if (a.size !== b.size) {
    return false;
  }
  for (const point of a) {
    if (!b.has(point)) {
      return false;
    }
  }
  return true;
}

/**
 * 抽取文本参数花括号内的文字，只有这些位置出现裸 % 才算 R12 违例。
 * 覆盖：title / xlabel / ylabel / zlabel / legend / legend entries / \addlegendentry / \node 文字。
 * @param {string} code LaTeX 代码
 * @returns {string[]} 文本片段列表
 */
function extractTextParamValues(code) {
  const values = [];
  const paramRe = /\b(?:title|xlabel|ylabel|zlabel|legend(?:\s+entries)?)\s*=\s*\{([^{}]*)\}/g;
  collect(paramRe, code, values);
  collect(/\\addlegendentry\s*\{([^{}]*)\}/g, code, values);
  // \node[...] at (x,y) {文字}：花括号前不含花括号，[^{}]* 精确停在第一处文字块
  collect(/\\node(?:\[[^\]]*\])?[^{}]*\{([^{}]*)\}/g, code, values);
  return values;
}

/** 把正则的所有第 1 捕获组依次推入 out。 */
function collect(regex, code, out) {
  let m = regex.exec(code);
  while (m !== null) {
    out.push(m[1]);
    m = regex.exec(code);
  }
}

/**
 * 花括号文本中是否存在未被反斜杠转义的 %（`\%` 合法）。
 * @param {string} text 花括号内文本
 * @returns {boolean}
 */
function hasUnescapedPercent(text) {
  return /(^|[^\\])%/.test(text);
}
