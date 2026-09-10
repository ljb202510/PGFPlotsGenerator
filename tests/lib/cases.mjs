// 用例清单（唯一事实来源）：与 docs/manual-test-cases.md §5/§6 的 01–10 对齐。
// prompt 为文档中的提示词原文，逐字照搬，勿改写。

export const CASES = [
  {
    id: '01',
    chart: '折线图',
    upload: true,
    file: 'docs/testdata/01-line-sales.csv',
    dataPreview: '1–12 月销售额（万元）：86, 92, 105, 98, 112, 126, 119, 138, 150, 143, 162, 175',
    prompt:
      '请根据我上传的数据绘制折线图，展示 1—12 月销售额的变化趋势；折线添加数据点标记并在每个数据点上方标注销售额数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。',
    checks: ['折线含 12 个数据点且数值与数据一致', '每点上方有数值标注且不重叠', '中文标题与坐标轴标签正常'],
  },
  {
    id: '02',
    chart: '柱状图',
    upload: true,
    file: 'docs/testdata/02-bar-workshop.csv',
    dataPreview: '一车间 4520、二车间 6100、三车间 3890、四车间 5230、五车间 2980（台）',
    prompt:
      '请根据我上传的数据绘制柱状图，对比各车间产量；在每根柱子上方标注产量数值（单位：台）；添加图表标题、X 轴（车间）与 Y 轴（产量/台）标签，并加上图例。',
    checks: ['5 根柱子，高度排序正确', '柱顶数值标注清晰不遮挡', 'Y 轴范围合理，最高柱完整显示'],
  },
  {
    id: '03',
    chart: '饼图',
    upload: true,
    file: 'docs/testdata/03-pie-share.xlsx',
    dataPreview: '品牌A 32%、品牌B 26%、品牌C 18%、品牌D 14%、品牌E 10%',
    prompt:
      '请根据我上传的数据绘制饼图，展示各品牌市场份额占比；为每个扇区标注品牌名称与百分比数值；添加图表标题与图例。',
    checks: ['5 个扇区，占比与数据一致', '各扇区含品牌名与百分比', '中文字体与图例渲染正常'],
  },
  {
    id: '04',
    chart: '散点图',
    upload: true,
    file: 'docs/testdata/04-scatter-ad-sales.csv',
    dataPreview: '广告费用（万元）与销量（件）共 10 组正相关数据（5,42）…（30,205）',
    prompt:
      '请根据我上传的数据绘制散点图，观察广告费用与销量之间的相关性；每个数据点使用清晰的圆形标记，不要添加趋势线；添加图表标题、X 轴（广告费用/万元）与 Y 轴（销量/件）标签。',
    checks: ['10 个散点呈正相关分布', 'X/Y 轴范围与数据匹配', '散点无越界、无图例遮挡'],
  },
  {
    id: '05',
    chart: '分组柱状图',
    upload: true,
    file: 'docs/testdata/05-groupedbar-products.xlsx',
    dataPreview: 'Q1 A45/B38；Q2 52/55；Q3 60/58；Q4 68/74（万件）',
    prompt:
      '请根据我上传的数据绘制分组柱状图，对比产品A与产品B在各季度的销量；两类产品使用不同颜色并在柱顶标注销量数值（单位：万件）；添加图表标题、X 轴（季度）与 Y 轴（销量/万件）标签，并加上图例。',
    checks: ['每季度并排两根柱子，共 4 组', '图例区分两类产品且颜色对应', 'Q4 两组 68/74 为最高且完整显示'],
  },
  {
    id: '06',
    chart: '双折线图',
    upload: true,
    file: 'docs/testdata/06-multiline-channels.csv',
    dataPreview: '1–12 月线下/线上渠道销售额（万元），线上约 9 月起反超线下',
    prompt:
      '请根据我上传的数据绘制折线图，对比线下渠道与线上渠道 1—12 月销售额变化趋势；两条折线使用不同颜色与标记，并标注数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。',
    checks: ['两条折线两种颜色，图例区分渠道', '9 月后线上反超线下，交叉点可见', '12 个月刻度与数值标注不重叠'],
  },
  {
    id: '07',
    chart: '堆积柱状图',
    upload: true,
    file: 'docs/testdata/07-stackedbar-regions.xlsx',
    dataPreview: 'Q1–Q4 北/中/南（万吨）：35/42/28、45/48/36、52/55/44、60/63/51',
    prompt:
      '请根据我上传的数据绘制堆积柱状图，展示北区、中区、南区三个区域各季度的销量累计情况；三个区域使用不同颜色分段堆积并加上图例；添加图表标题、X 轴（季度）与 Y 轴（销量/万吨）标签。',
    checks: ['每季度一根堆积柱，三种颜色分段', '图例三色对应正确', 'Q4 总高约 174 不超出 Y 轴范围'],
  },
  {
    id: '08',
    chart: '大数值柱状图',
    upload: true,
    file: 'docs/testdata/08-budget-city.csv',
    dataPreview: '上海 8313、北京 6182、深圳 3712、苏州 2456、杭州 2017、广州 1979（亿元）',
    prompt:
      '请根据我上传的数据绘制柱状图，展示各城市的一般公共预算收入；在每根柱子上方标注收入数值（单位：亿元）；添加图表标题、X 轴（城市）与 Y 轴（一般公共预算收入/亿元）标签，并加上图例。请确保 Y 轴数值与数据保持同一单位量级。',
    checks: ['6 根柱，上海最高（8313）', 'Y 轴刻度单位与柱高对应', '数值为真实千位量级，无异常刻度'],
  },
  {
    id: '09',
    chart: '公开数据兜底柱状图',
    upload: false,
    file: null,
    dataPreview: '无文件；AI 基于公开统计给出近五年死亡人口（万人，约 900–1200）',
    prompt:
      '请绘制近五年（2019—2023 年）全国死亡人口的柱状图，并在每根柱子上方标注数值（单位：万人）；添加图表标题、X 轴（年份）与 Y 轴（出生人口/万人）标签，并加上图例。',
    checks: ['无需上传文件即可生成 5 根柱', '数值为“万人”量级、年份 2019—2023', '编译与预览正常，无空气泡'],
  },
  {
    id: '10',
    chart: '内联数据柱状图',
    upload: false,
    file: null,
    dataPreview: '无文件；提示词内联 2019–2023 年粮食产量 3200/3280/3360/3450/3520（万吨）',
    prompt:
      '请根据下面的数据绘制柱状图：某省近五年粮食产量分别为 2019 年 3200 万吨、2020 年 3280 万吨、2021 年 3360 万吨、2022 年 3450 万吨、2023 年 3520 万吨。请在每根柱子上方标注产量数值（单位：万吨）；添加图表标题、X 轴（年份）与 Y 轴（粮食产量/万吨）标签。',
    checks: ['5 根柱逐年递增（3200→3520），数据与提示词一致', 'Y 轴按“万吨”标度，范围约 3000–3600', '编译与预览正常'],
  },
];

/**
 * 按 --cases 过滤；支持 "01,02,05" 或 "01-05"。
 * @param {string|undefined} spec
 */
export function pickCases(spec) {
  if (!spec || spec === true) return CASES;
  const wanted = new Set();
  for (const part of String(spec).split(',').map((s) => s.trim()).filter(Boolean)) {
    const range = part.match(/^(\d+)\s*-\s*(\d+)$/);
    if (range) {
      const [a, b] = [Number(range[1]), Number(range[2])];
      for (let n = Math.min(a, b); n <= Math.max(a, b); n++) wanted.add(String(n).padStart(2, '0'));
    } else {
      wanted.add(part.padStart(2, '0'));
    }
  }
  return CASES.filter((c) => wanted.has(c.id));
}
