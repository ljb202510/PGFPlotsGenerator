#!/usr/bin/env node
/**
 * 自动化回归脚本：把 docs/manual-test-cases-checklist.md 里每个用例的
 * 「提示词 + 数据文件」通过后端真实 API 发给 AI（不模拟前端 UI），
 * 并以真实用户身份落库历史记录（同前端 /api/history，可在页面查到）。
 *
 * 终端默认按 GBK 解码 → 脚本打印统一走 console.error，Node 用 process.stdout 写摘要时
 * 仅输出 ASCII（结果走 JSON 文件），避免中文乱码干扰可读性。
 *
 * 用法（在 spring-backend 目录，后端已启动于 http://localhost:3000）：
 *     EMAIL=x@x.com PASSWORD=xxx node run-cases.mjs
 *     node run-cases.mjs --email=x@x.com --password=xxx --base=http://localhost:3000
 *     node run-cases.mjs --model=siliconflow --only=25,27,32
 *     node run-cases.mjs --compile            # 生成后额外提交 XeLaTeX 编译并轮询 PDF 状态
 *     node run-cases.mjs --no-upload          # 跳过「先上传、用上传得到的 data_id」的整条链路（仅测试用）
 *     node run-cases.mjs --rag=on --tag=rag-on-32cases   # 仅给本轮打 RAG 开关标记（真正生效靠进程环境变量 RAG_ENABLED）
 *
 * 产出：控制台汇总 + test/results/<tag>.json。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { detectViolations } from './eval/violations.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function arg(name, def) {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  if (hit) return hit.split('=').slice(1).join('=');
  return def;
}
const hasFlag = (name) => process.argv.includes(`--${name}`);

const BASE = (process.env.VERIFY_BASE || arg('base', 'http://localhost:3000')).replace(/\/$/, '');
const EMAIL = process.env.EMAIL || arg('email', '');
const PASSWORD = process.env.PASSWORD || arg('password', '');
const MODEL = arg('model', 'qwen');
const ONLY = arg('only', '').split(',').filter(Boolean);
const COMPILE = hasFlag('compile');
// [RAG on/off 对比] 仅为产物打标与留档，不影响服务端行为（开关由进程环境变量 RAG_ENABLED 决定）
const RAG_TAG = arg('rag', '');

const TESTDATA = path.join(__dirname, '..', 'docs', 'testdata');

/**
 * 从 checklist 同步的用例表：id / 名 / 数据文件（复用文件标注在注释）/ 提示词。
 * case 17 已放弃，skip 置空仅记录。
 */
const CASES = [
  // ---------- 基础 01–10 ----------
  { id: '01', name: '折线图', file: '01-line-sales.csv', prompt: '请根据我上传的数据绘制折线图，展示 1—12 月销售额的变化趋势；折线添加数据点标记并在每个数据点上方标注销售额数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '02', name: '柱状图', file: '02-bar-workshop.csv', prompt: '请根据我上传的数据绘制柱状图，对比各车间产量；在每根柱子上方标注产量数值（单位：台）；添加图表标题、X 轴（车间）与 Y 轴（产量/台）标签，并加上图例。' },
  { id: '03', name: '饼图', file: '03-pie-share.xlsx', prompt: '请根据我上传的数据绘制饼图，展示各品牌市场份额占比；为每个扇区标注品牌名称与百分比数值；添加图表标题与图例。' },
  { id: '04', name: '散点图', file: '04-scatter-ad-sales.csv', prompt: '请根据我上传的数据绘制散点图，观察广告费用与销量之间的相关性；每个数据点使用清晰的圆形标记，不要添加趋势线；添加图表标题、X 轴（广告费用/万元）与 Y 轴（销量/件）标签。' },
  { id: '05', name: '分组柱状图', file: '05-groupedbar-products.xlsx', prompt: '请根据我上传的数据绘制分组柱状图，对比产品A与产品B在各季度的销量；两类产品使用不同颜色并在柱顶标注销量数值（单位：万件）；添加图表标题、X 轴（季度）与 Y 轴（销量/万件）标签，并加上图例。' },
  { id: '06', name: '双折线图', file: '06-multiline-channels.csv', prompt: '请根据我上传的数据绘制折线图，对比线下渠道与线上渠道 1—12 月销售额变化趋势；两条折线使用不同颜色与标记，并标注数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '07', name: '堆积柱状图', file: '07-stackedbar-regions.xlsx', prompt: '请根据我上传的数据绘制堆积柱状图，展示北区、中区、南区三个区域各季度的销量累计情况；三个区域使用不同颜色分段堆积并加上图例；添加图表标题、X 轴（季度）与 Y 轴（销量/万吨）标签。' },
  { id: '08', name: '大数值柱状图', file: '08-budget-city.csv', prompt: '请根据我上传的数据绘制柱状图，展示各城市的一般公共预算收入；在每根柱子上方标注收入数值（单位：亿元）；添加图表标题、X 轴（城市）与 Y 轴（一般公共预算收入/亿元）标签，并加上图例。请确保 Y 轴数值与数据保持同一单位量级。' },
  { id: '09', name: '公开数据', file: null, prompt: '请绘制近五年（2019—2023 年）全国死亡人口的柱状图，并在每根柱子上方标注数值（单位：万人）；添加图表标题、X 轴（年份）与 Y 轴（出生人口/万人）标签，并加上图例。' },
  { id: '10', name: '内联数据', file: null, prompt: '请根据下面的数据绘制柱状图：某省近五年粮食产量分别为 2019 年 3200 万吨、2020 年 3280 万吨、2021 年 3360 万吨、2022 年 3450 万吨、2023 年 3520 万吨。请在每根柱子上方标注产量数值（单位：万吨）；添加图表标题、X 轴（年份）与 Y 轴（粮食产量/万吨）标签。' },
  // ---------- 进阶 11–32 ----------
  { id: '11', name: '面积图', file: '11-area-greening.csv', prompt: '请根据我上传的数据绘制面积图，展示 2015—2024 年建成区绿化覆盖率的变化；折线下方填充淡色区域；添加图表标题、X 轴（年份）与 Y 轴（绿化覆盖率/%）标签，并加上图例。' },
  { id: '12', name: '水平条形图', file: '12-hbar-metro.csv', prompt: '请根据我上传的数据绘制水平条形图，对比各城市轨道交通运营里程；在每根条形末端标注里程数值（单位：km）；添加图表标题、X 轴（运营里程/km）与 Y 轴（城市）标签，并加上图例。' },
  { id: '13', name: '直方图', file: '13-hist-scores.csv', prompt: '请根据我上传的数据绘制直方图，展示某班成绩各分数段的人数分布；在每根柱子上方标注人数；添加图表标题、X 轴（分数段）与 Y 轴（人数）标签。' },
  { id: '14', name: '误差棒柱状图', file: '14-errorbar-yield.csv', prompt: '请根据我上传的数据绘制带误差棒的柱状图，展示各施肥方案的平均亩产及其标准差；误差棒表示标准差；在柱顶标注平均亩产数值（单位：公斤）；添加图表标题、X 轴（施肥方案）与 Y 轴（平均亩产/公斤）标签，并加上图例。' },
  { id: '15', name: '对数轴折线', file: '15-logaxis-users.csv', prompt: '请根据我上传的数据绘制折线图，展示 2015—2024 年平台注册用户数的增长（呈指数增长）；Y 轴使用对数坐标；折线添加数据点标记并标注数值（单位：万人）；添加图表标题、X 轴（年份）与 Y 轴（注册用户数/万人，对数刻度）标签，并加上图例。' },
  { id: '16', name: '平滑折线', file: '16-smooth-temp.csv', prompt: '请根据我上传的数据绘制平滑曲线图，展示 1—12 月平均气温变化（呈倒 U 形）；曲线平滑过渡；添加图表标题、X 轴（月份）与 Y 轴（平均气温/℃）标签，并加上图例。' },
  { id: '17', name: '堆叠面积图', file: '17-stackedarea-products.xlsx', skip: true, prompt: '请根据我上传的数据绘制堆叠面积图，展示产品甲、产品乙、产品丙 1—12 月的销量构成与累计趋势；三个产品使用不同颜色自下而上堆叠并加上图例；添加图表标题、X 轴（月份）与 Y 轴（销量/万件）标签。' },
  { id: '18', name: '百分比堆积柱状图', file: '18-pctstack-expense.xlsx', prompt: '请根据我上传的数据绘制百分比堆积柱状图，展示华北、华东、华南、西部四个地区财政支出中教育、医疗、交通、其他四项的占比结构；四个地区各为一根柱，四项自下而上堆叠至 100% 并加上图例；添加图表标题、X 轴（地区）与 Y 轴（占比/%）标签。' },
  { id: '19', name: '图例不遮挡(R1)', file: '08-budget-city.csv', prompt: '请根据我上传的数据绘制柱状图，展示各城市一般公共预算收入（数据整体由高到低）；在每根柱子上方标注收入数值（单位：亿元）；添加图表标题、X 轴（城市）与 Y 轴（一般公共预算收入/亿元）标签，并加上图例。' },
  { id: '20', name: '轴外无文字(R2)', file: '02-bar-workshop.csv', prompt: '请根据我上传的数据绘制柱状图，对比各车间产量；在每根柱子上方标注产量数值（单位：台）；请在图表内部注明「数据来源：某厂 2024 年度统计」；添加图表标题、X 轴（车间）与 Y 轴（产量/台）标签，并加上图例。' },
  { id: '21', name: '标注成对(R3)', file: '01-line-sales.csv', prompt: '请根据我上传的数据绘制折线图，展示 1—12 月销售额变化趋势；折线添加圆形数据点标记，并在每个数据点上方标注销售额数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '22', name: '单位量级(R4)', file: '22-expense-raw.csv', prompt: '请根据我上传的数据绘制柱状图，展示各城市一般公共预算支出；在每根柱子上方标注支出数值；添加图表标题、X 轴（城市）与 Y 轴（一般公共预算支出）标签，并加上图例。请确保 Y 轴数值与数据保持同一单位量级，避免刻度异常。' },
  { id: '23', name: '禁误差棒(R5)', file: '01-line-sales.csv', prompt: '请根据我上传的数据绘制折线图，展示 1—12 月销售额的波动情况；折线添加数据点标记并标注销售额数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '24', name: '单图结构(R6)', file: '04-scatter-ad-sales.csv', prompt: '请根据我上传的数据生成一张完整、规范的散点图，观察广告费用与销量之间的相关性；请确保图表包含完整的标题、坐标轴标签，并在图中给出必要说明（如相关关系）；每个数据点使用清晰的圆形标记。' },
  { id: '25', name: '半角逗号(R7)', file: '25-provinces-gdp.csv', prompt: '请根据我上传的数据绘制柱状图，对比这 14 个省份/直辖市的 GDP；在每根柱子上方标注 GDP 数值（单位：亿元）；添加图表标题、X 轴（省份）与 Y 轴（GDP/亿元）标签，并加上图例。' },
  { id: '26', name: '标注错开(R8)', file: '26-multiline3-months.csv', prompt: '请根据我上传的数据绘制折线图，对比华北、华东、华南三个区域 1—12 月销售额变化趋势；三条折线使用不同颜色与数据点标记，并标注数值（单位：万元）；添加图表标题、X 轴（月份）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '27', name: '负值', file: '27-negative-profit.csv', prompt: '请根据我上传的数据绘制柱状图，展示公司 2023—2024 各季度净利润；负值用与正值不同（或相反方向）的柱子表示；在每根柱子上方/下方标注数值（单位：万元）；添加图表标题、X 轴（季度）与 Y 轴（净利润/万元）标签，并加上图例。' },
  { id: '28', name: '单数据点', file: '28-single-point.csv', prompt: '请根据我上传的数据绘制柱状图；数据只有一个类别；在柱子上方标注投资额数值（单位：万元）；添加图表标题、X 轴（项目）与 Y 轴（投资额/万元）标签。' },
  { id: '29', name: '超长中文标签', file: '29-longlabel-regions.xlsx', prompt: '请根据我上传的数据绘制柱状图，对比各区域销售额；X 轴为较长的中文区域名称，请合理处理标签（如旋转或缩小字号）避免重叠；在每根柱子上方标注销售额（单位：万元）；添加图表标题、X 轴（区域）与 Y 轴（销售额/万元）标签，并加上图例。' },
  { id: '30', name: '极小/极大值', file: '30-extreme-invest.csv', prompt: '请根据我上传的数据绘制柱状图，展示各项目投资额；注意数据量级差异极大；在每根柱子上方标注投资额（单位：万元）；添加图表标题、X 轴（项目）与 Y 轴（投资额/万元）标签，并加上图例。' },
  { id: '31', name: '小数/百分比', file: '31-conversion-rate.csv', prompt: '请根据我上传的数据绘制柱状图，对比各渠道转化率；在每根柱子上方标注转化率数值，保留两位小数并带百分号（%）；添加图表标题、X 轴（渠道）与 Y 轴（转化率/%）标签，并加上图例。' },
  { id: '32', name: '退化值', file: '32-equal-quarters.csv', prompt: '请根据我上传的数据绘制折线图，展示各季度销量；折线添加数据点标记并标注数值（单位：万件）；添加图表标题、X 轴（季度）与 Y 轴（销量/万件）标签，并加上图例。' },
];

// ---------- 工具 ----------
async function request(method, url, { auth, form, json } = {}) {
  const headers = {};
  if (auth) headers.Authorization = `Bearer ${auth}`;
  let body;
  if (form) { body = form; }
  else if (json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  const res = await fetch(BASE + url, { method, headers, body });
  const text = await res.text();
  let parsed = null;
  try { parsed = JSON.parse(text); } catch { /* 非 JSON */ }
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${parsed?.message || text.slice(0, 200)}`);
  return parsed;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 每个数据文件只上传一次，按文件名缓存 data_id
const uploadedCache = new Map();

async function ensureUploaded(token, file) {
  if (!file) return null;
  const key = file;
  if (uploadedCache.has(key)) return uploadedCache.get(key);
  const full = path.join(TESTDATA, file);
  if (!fs.existsSync(full)) throw new Error(`数据文件不存在: ${full}`);
  const buf = fs.readFileSync(full);
  const nameNoExt = path.basename(file).replace(/\.[^/.]+$/, '');
  const form = new FormData();
  form.append('name', nameNoExt);
  form.append('description', `自动化回归用例数据：${nameNoExt}`);
  form.append('file', new Blob([buf]), path.basename(file));
  const res = await request('POST', '/api/datasets', { auth: token, form });
  const dataId = res?.data?.data_id;
  if (dataId == null) throw new Error(`上传 ${file} 失败: ${JSON.stringify(res)}`);
  uploadedCache.set(key, dataId);
  return dataId;
}

async function runCase(token, c) {
  const dataId = await ensureUploaded(token, c.file);
  const json = { message: c.prompt, model: MODEL };
  if (dataId != null) json.data_ids = [dataId];
  const startedAt = Date.now();
  const res = await request('POST', '/api/chat', { auth: token, json });
  const latencyMs = Date.now() - startedAt;
  const d = res?.data || {};
  const chartCode = d.chart_code || '';
  // 图表质量自动层：复用 eval/eval.mjs 的同一套规则，避免「离线评估口径」与本次口径分裂
  const violations = chartCode.trim() ? detectViolations(chartCode) : [];
  const rec = {
    id: c.id,
    name: c.name,
    ok: !!(chartCode && d.history_id),
    history_id: d.history_id ?? null,
    chart_code_length: d.chart_code_length ?? chartCode.length,
    chart_code: chartCode,
    model_used: d.model_used ?? null,
    reply_preview: (d.reply || '').slice(0, 80),
    latency_ms: latencyMs,
    violation_free: violations.length === 0,
    violations: violations.map((v) => v.rule),
  };
  if (COMPILE && rec.history_id) rec.compile = await compileHistory(token, rec.history_id);
  return rec;
}

async function compileHistory(token, historyId) {
  const sub = await request('POST', `/api/compile/${historyId}`, { auth: token });
  const taskId = sub?.data?.task_id;
  if (!taskId) return { status: 'submit_failed', error: 'no task_id' };
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    const st = await request('GET', `/api/compile/task/${taskId}`, { auth: token });
    const s = st?.data;
    if (s?.status === 'success') return { status: 'success', file_size: s.file_size, duration_ms: s.duration_ms };
    if (s?.status === 'failed') return { status: 'failed', error: (s.error || '').slice(0, 500) };
    await sleep(1500);
  }
  return { status: 'timeout' };
}

// ---------- 主流程 ----------
async function main() {
  if (!EMAIL || !PASSWORD) {
    console.error('缺少账号。用法：EMAIL=xxx PASSWORD=yyy node run-cases.mjs');
    process.exit(2);
  }
  const login = await request('POST', '/api/auth/login', { json: { email: EMAIL, password: PASSWORD } });
  const token = login?.data?.token;
  if (!token) {
    console.error('登录失败:', JSON.stringify(login));
    process.exit(2);
  }

  const selected = CASES.filter((c) => (ONLY.length ? ONLY.includes(c.id) : true));
  const results = [];
  for (const c of selected) {
    if (c.skip) { results.push({ id: c.id, name: c.name, skip: true }); continue; }
    try {
      const rec = await runCase(token, c);
      results.push(rec);
      console.error(`[${rec.ok ? 'OK ' : 'FAIL'}] ${rec.id} ${rec.name} history=${rec.history_id} len=${rec.chart_code_length} ${rec.compile ? 'compile=' + rec.compile.status : ''}`);
    } catch (e) {
      results.push({ id: c.id, name: c.name, error: e.message });
      console.error(`[ERR ] ${c.id} ${c.name}: ${e.message}`);
    }
  }

  // 汇总（ASCII 输出，防 GBK 终端乱码；中文明细写入 JSON）
  const ok = results.filter((r) => r.ok).length;
  const skipped = results.filter((r) => r.skip).length;
  const failed = results.filter((r) => r.error || (r.ok === false)).length;
  const executed = results.filter((r) => !r.skip);
  const passed = executed.filter((r) => r.ok).length;
  console.error(`\nSUMMARY: executed=${executed.length} passed=${passed} failed=${failed} skipped=${skipped} total=${CASES.length}`);
  console.error(`model=${MODEL} base=${BASE} account=${EMAIL}`);

  // [RAG on/off 对比] 与 eval/eval.mjs 同口径指标：通过 = 有代码 且 编译成功；零违例通过 = 通过 且 无静态违例
  const compiledOk = (r) => r.compile?.status === 'success';
  const hasCode = executed.filter((r) => r.ok);
  const pass = hasCode.filter(compiledOk).length;
  const zeroViolationPass = hasCode.filter((r) => compiledOk(r) && r.violation_free).length;
  const lats = executed.map((r) => r.latency_ms).filter((x) => typeof x === 'number');
  const sortedLats = [...lats].sort((a, b) => a - b);
  const violationCounts = {};
  for (const r of executed) for (const rule of r.violations || []) violationCounts[rule] = (violationCounts[rule] || 0) + 1;
  const metrics = {
    total: CASES.length,
    executed: executed.length,
    code_rate: +(executed.length ? hasCode.length / executed.length : 0).toFixed(3),
    compile_rate: +(hasCode.length ? pass / hasCode.length : 0).toFixed(3),
    first_pass_rate: +(executed.length ? pass / executed.length : 0).toFixed(3),
    zero_violation_rate: +(executed.length ? zeroViolationPass / executed.length : 0).toFixed(3),
    violation_counts: violationCounts,
    degraded_count: executed.filter((r) => r.model_used && r.model_used !== MODEL).length,
    avg_latency_ms: lats.length ? Math.round(lats.reduce((a, b) => a + b, 0) / lats.length) : 0,
    p95_latency_ms: sortedLats.length ? sortedLats[Math.ceil(sortedLats.length * 0.95) - 1] : 0,
  };
  console.error(`METRICS: code=${metrics.code_rate} compile=${metrics.compile_rate} first_pass=${metrics.first_pass_rate} zero_violation=${metrics.zero_violation_rate} avg=${metrics.avg_latency_ms}ms p95=${metrics.p95_latency_ms}ms degraded=${metrics.degraded_count}`);
  console.error(`VIOLATIONS: ${JSON.stringify(metrics.violation_counts)}`);
  console.error(`RAG: tag=${RAG_TAG || '(unset)'} RAG_ENABLED(env)=${process.env.RAG_ENABLED ?? '(unset)'}`);

  const tag = arg('tag', new Date().toISOString().replace(/[:.]/g, '-'));
  const outDir = path.join(__dirname, 'test', 'results');
  fs.mkdirSync(outDir, { recursive: true });
  const outFile = path.join(outDir, `${tag}.json`);
  fs.writeFileSync(outFile, JSON.stringify({ runAt: new Date().toISOString(), base: BASE, model: MODEL, email: EMAIL, rag: RAG_TAG || null, rag_env: process.env.RAG_ENABLED ?? null, compile: COMPILE, metrics, results }, null, 2), 'utf8');
  console.error(`results -> ${outFile}`);

  // 0 无法准确判断“通过了”与“仅是跑通”，非零仅表示有执行失败/异常
  process.exitCode = 0;
}

main().catch((e) => {
  console.error('运行失败:', e);
  process.exit(1);
});