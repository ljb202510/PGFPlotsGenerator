/**
 * PGFPlotsGenerator - 编译提交延迟实测脚本（Node 版，无第三方依赖）
 *
 * 用途：量出「编译改为异步任务契约（G1）」后，POST /api/compile/{history_id} 的**提交耗时**
 *       —— 即「提交即返回 task_id」有多快，对照改造前「同步等待编译、最长 30s（LATEX_TIMEOUT_MS）」。
 *       2026-09-14 批次2 验收曾手测得到「4 任务并发提交响应 14–19ms」，但没有脚本固化；
 *       本脚本把该测量变成可重复执行的一条命令。
 *
 * 用法（在 spring-backend 目录，后端已启动；账号需名下有 ≥ count 条带代码的历史）：
 *     node measure-submit-latency.mjs --email=you@example.com --password=yourpwd
 *     node measure-submit-latency.mjs --email=... --password=... --count=4 --rounds=3
 *     node measure-submit-latency.mjs --email=... --password=... --base=http://localhost:3000
 *
 * 选项：
 *     --base    后端地址（默认 http://localhost:3000，或环境变量 MEASURE_BASE）
 *     --email   登录邮箱（或环境变量 MEASURE_EMAIL）
 *     --password 登录密码（或环境变量 MEASURE_PASSWORD）
 *     --admin   管理员账号名（可选：上面那步用户登录失败时，改走 /api/auth/admin/login，如 --admin=admin123）
 *     --count   每轮并发提交数（默认 4）
 *     --rounds  重复轮数（默认 3）
 *     --timeout 单请求超时 ms（默认 10000）
 *     --no-wait 不轮询到任务终态（默认会轮询，用于确认任务确实被接受）
 *
 * 注意：参数值不要加尖括号。cmd 会把 `<` 当成输入重定向，直接报「系统找不到指定的文件」
 *       （本脚本对两端尖括号做了容错，但仍推荐写成 --email=you@example.com 这种标准形式）。
 *
 * 依赖：本机 MySQL 已启动且库 X 已初始化；本脚本**不触发真正编译**（只量提交），
 *       但仍会等任务终态（需要本机 XeLaTeX；用 --no-wait 可跳过等待）。
 *
 * 口径说明：
 *     - 量的是 **POST 的 HTTP 响应时间**（提交耗时），不是编译耗时；
 *     - 每轮用 count 个**并行**请求，模拟「多个任务同时提交」；
 *     - 「改造前同步阻塞最长 30s」= LATEX_TIMEOUT_MS 默认 30000（application.yml）。
 */
const BASE = stripBrackets(process.env.MEASURE_BASE || argOf('--base') || 'http://localhost:3000').replace(/\/$/, '');
const EMAIL = stripBrackets(process.env.MEASURE_EMAIL || argOf('--email') || '');
const PASSWORD = stripBrackets(process.env.MEASURE_PASSWORD || argOf('--password') || '');
const ADMIN_ACCOUNT = stripBrackets(argOf('--admin') || '');
const COUNT = Number(argOf('--count') || 4);
const ROUNDS = Number(argOf('--rounds') || 3);
const TIMEOUT = Number(argOf('--timeout') || 10000);
const NO_WAIT = process.argv.includes('--no-wait');
const POLL_INTERVAL_MS = 500;
const POLL_MAX_MS = 120000;

function argOf(name) {
  const hit = process.argv.find((a) => a.startsWith(`${name}=`));
  return hit ? hit.slice(name.length + 1) : undefined;
}

/**
 * 去掉参数两端的尖括号/空白：在 cmd 里粘贴 `--email=<you@example.com>` 时，`<` 会被当成输入重定向，
 * 报「系统找不到指定的文件」（脚本根本不会执行）；这里做一次容错，让带尖括号的写法也能跑。
 */
function stripBrackets(value) {
  return String(value || '').replace(/^[<\s]+/, '').replace(/[>\s]+$/, '');
}

function nowMs() {
  return Number(process.hrtime.bigint() / 1000000n);
}

/** 一次请求；返回 {status, ok, json, ms}，不抛异常 */
async function req(method, url, { token, body, timeout = TIMEOUT } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const started = nowMs();
  try {
    const res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeout),
    });
    let json = null;
    try {
      json = await res.json();
    } catch (e) {
      // 非 JSON 响应（如 PDF 流）
    }
    return { status: res.status, ok: res.ok, json, ms: nowMs() - started };
  } catch (e) {
    return { status: 0, ok: false, json: null, ms: nowMs() - started, error: String(e && e.message) };
  }
}

function pct(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

function stats(nums) {
  const sorted = [...nums].sort((a, b) => a - b);
  return {
    n: sorted.length,
    min: sorted[0] || 0,
    median: pct(sorted, 50),
    p95: pct(sorted, 95),
    max: sorted[sorted.length - 1] || 0,
  };
}

async function main() {
  if (!EMAIL || !PASSWORD) {
    console.error('缺少账号：请传 --email=... --password=...（或设置 MEASURE_EMAIL / MEASURE_PASSWORD）');
    console.error('示例：node measure-submit-latency.mjs --email=you@example.com --password=yourpwd');
    process.exit(2);
  }

  console.log(`目标后端：${BASE}`);
  console.log(`参数：并发 ${COUNT} × ${ROUNDS} 轮（每请求超时 ${TIMEOUT}ms，${NO_WAIT ? '不等任务终态' : '等待任务终态'}）`);

  // ---------------- 1. 登录（用户登录优先，失败且给了 --admin 时改走管理员登录） ----------------
  let login = await req('POST', `${BASE}/api/auth/login`, { body: { email: EMAIL, password: PASSWORD } });
  let token = login.json && login.json.data && login.json.data.token;
  let loginPath = '用户登录 /api/auth/login';

  if (!token && ADMIN_ACCOUNT) {
    login = await req('POST', `${BASE}/api/auth/admin/login`, {
      body: { adminAccount: ADMIN_ACCOUNT, password: PASSWORD },
    });
    token = login.json && login.json.data && login.json.data.token;
    loginPath = `管理员登录 /api/auth/admin/login（账号 ${ADMIN_ACCOUNT}）`;
  }

  if (!token) {
    const msg = login.json ? login.json.message : login.error || '';
    console.error(`登录失败：status=${login.status} ${msg}`);
    if (login.status === 0) {
      console.error(`提示：连不上后端 ${BASE} —— 请先启动后端（scripts\\run.cmd 或 java -jar target\\pgfplots-backend-1.0.0.jar）`);
    } else {
      console.error('提示：① 参数值不要带尖括号：--email=you@example.com --password=yourpwd');
      console.error('      ② 管理员账号走 /api/auth/login 需要该邮箱在 user 表里；若走不通，改用');
      console.error('         node measure-submit-latency.mjs --email=admin@pgfplots.com --password=666666 --admin=admin123');
    }
    process.exit(1);
  }
  console.log(`登录成功（${loginPath}，提交耗时 ${login.ms}ms）`);

  // ---------------- 2. 取可编译的历史 ----------------
  const list = await req('GET', `${BASE}/api/history?page=1&limit=100`, { token });
  const records = (list.json && list.json.data && list.json.data.records) || [];
  const usable = records.filter((r) => r && r.id && typeof r.chart_code === 'string' && r.chart_code.trim() !== '');
  if (usable.length < COUNT) {
    console.error(`可编译历史不足：需要 ${COUNT} 条，实际 ${usable.length} 条（记录总数 ${records.length}）`);
    console.error('提示：先在页面上生成几张图，或换一个历史更多的账号。');
    process.exit(1);
  }
  const targets = usable.slice(0, COUNT);
  console.log(`选中历史：${targets.map((r) => `#${r.id}`).join(' ')}（代码长度 ${targets.map((r) => r.chart_code.length).join('/')}）\n`);

  // ---------------- 3. 并发提交并测量 ----------------
  const all = [];
  const tasks = [];
  for (let round = 1; round <= ROUNDS; round += 1) {
    const started = nowMs();
    const results = await Promise.all(
      targets.map((r) => req('POST', `${BASE}/api/compile/${r.id}`, { token })),
    );
    const wall = nowMs() - started;

    const line = results.map((r, i) => {
      const taskId = r.json && r.json.data && (r.json.data.task_id || r.json.data.taskId);
      if (taskId) tasks.push({ taskId, historyId: targets[i].id });
      const okMark = r.ok && taskId ? 'ok' : `FAIL(${r.status})`;
      all.push(r.ms);
      return `${okMark} ${String(r.ms).padStart(5)}ms`;
    });
    console.log(`第 ${round} 轮  ${line.join(' | ')}   整轮墙钟 ${wall}ms`);
  }

  const s = stats(all);
  console.log('\n---------------- 提交耗时（POST /api/compile/{id}） ----------------');
  console.log(`样本 ${s.n} 次：min ${s.min}ms / median ${s.median}ms / p95 ${s.p95}ms / max ${s.max}ms`);
  console.log(`对照：改造前同步等待编译，最长 30s（LATEX_TIMEOUT_MS=30000）`);
  console.log(`机器可读：SUBMIT_LATENCY_MS min=${s.min} median=${s.median} p95=${s.p95} max=${s.max} n=${s.n}`);

  // ---------------- 4. 等任务终态（证明任务确实被接受，不静默丢弃） ----------------
  if (!NO_WAIT && tasks.length > 0) {
    console.log(`\n---------------- 任务终态（${tasks.length} 个） ----------------`);
    const deadline = nowMs() + POLL_MAX_MS;
    const pending = new Map(tasks.map((t) => [t.taskId, t]));
    const finished = new Map();
    while (pending.size > 0 && nowMs() < deadline) {
      await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
      for (const [taskId, meta] of [...pending.entries()]) {
        const res = await req('GET', `${BASE}/api/compile/task/${taskId}`, { token });
        const data = res.json && res.json.data;
        const status = data && data.status;
        if (status && status !== 'queued' && status !== 'running') {
          finished.set(taskId, { ...meta, status, durationMs: data.duration_ms });
          pending.delete(taskId);
        }
      }
    }
    for (const [taskId, info] of finished.entries()) {
      console.log(`  ${taskId}  history#${info.historyId}  ${info.status}  duration_ms=${info.durationMs}`);
    }
    if (pending.size > 0) {
      console.log(`  [WARN] 仍有 ${pending.size} 个任务未在 ${POLL_MAX_MS / 1000}s 内到达终态：${[...pending.keys()].join(' ')}`);
    }
    const failed = [...finished.values()].filter((f) => f.status !== 'success').length;
    if (failed > 0) {
      console.log(`  [WARN] ${failed} 个任务终态非 success（编译失败或队列满，与提交延迟无关）`);
    }
  }

  const submitFailed = all.length === 0;
  process.exit(submitFailed ? 1 : 0);
}

main().catch((e) => {
  console.error(`[FATAL] ${e && e.message ? e.message : e}`);
  process.exit(1);
});
