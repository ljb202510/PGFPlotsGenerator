/**
 * 静态违例检测（批次3/评估集 V2，零依赖）。
 *
 * 依据：util/PromptTemplates.java 的【图表渲染规则】R1-R8 做正则启发式检查。
 * 定位：把「通过」细化为「通过且零违例」，用于量化 RAG / 提示词迭代的真实增量。
 *
 * 注意：
 * - 本模块是**启发式**检测，不追求零误报；命中即提示人工复核，不要当作硬性断言。
 * - **R4（数值与坐标轴单位/量级一致）故意不做静态检测**：它要求理解数据语义标度
 *   （如「万元 vs 元」「人 vs 万人」），正则会大量误报，产出不可信的评估数字。
 *   该项保留为人工评审。
 * - R7 只检查 LaTeX 结构上下文（symbolic x coords / coordinates 花括号内）里的全角逗号；
 *   不对整份代码做全角逗号扫描，避免把中文标签里的正常标点（如「数据来源：统计局，2024」）
 *   误判为违例。
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
