/**
 * PGFPlotsGenerator Java 后端 - 冒烟验证脚本（Node 版，无第三方依赖）
 *
 * 用法（在 spring-backend 目录）：
 *     node verify.js               # 自动启动后端并全量验证
 *     node verify.js --no-start    # 后端已在运行时只做接口验证
 *     set VERIFY_BASE=http://localhost:3000
 *
 * 覆盖：鉴权 / 管理后台 / 用户侧查询 / 数据集增删改查 / XeLaTeX 编译 / PDF 鉴权流式返回
 * 依赖：本机 MySQL 已启动且库 X 已初始化；编译用例需要 XeLaTeX。
 */
const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const http = require('http');
const path = require('path');

const BASE = process.env.VERIFY_BASE || 'http://localhost:3000';
const JAR = 'target/pgfplots-backend-1.0.0.jar';
const ROOT = __dirname;
process.chdir(ROOT);

const noStart = process.argv.includes('--no-start');

let pass = 0;
let fail = 0;
let warn = 0;

function check(name, ok, detail = '') {
  if (ok) {
    pass += 1;
    console.log(`  [PASS] ${name}`);
  } else {
    fail += 1;
    console.log(`  [FAIL] ${name}   ${detail}`);
  }
}

function warnMsg(name, detail = '') {
  warn += 1;
  console.log(`  [WARN] ${name}   ${detail}`);
}

function log(title) {
  console.log(`\n================ ${title} ================`);
}

/** 在常见位置查找 JDK 17+ */
function findJava() {
  const candidates = [
    process.env.PG_JAVA_HOME,
    'C:\\Program Files\\Microsoft\\jdk-21.0.2.13-hotspot',
    'C:\\Program Files\\Java\\jdk-21',
    'C:\\Program Files\\Java\\jdk-21.0.2',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.2.13-hotspot',
    'C:\\Program Files\\Java\\jdk-17',
    'C:\\Program Files\\Java\\jdk-23',
  ].filter(Boolean);
  for (const dir of candidates) {
    const exe = path.join(dir, 'bin', 'java.exe');
    if (fs.existsSync(exe)) return exe;
  }
  return null;
}

async function req(method, url, { token, body, timeout = 15000 } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
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
    // 非 JSON 响应
  }
  return { status: res.status, ok: res.ok, json };
}

async function waitReady(proc, seconds, base = BASE) {
  for (let i = 0; i < seconds; i += 1) {
    try {
      await fetch(`${base}/api/notice`, { signal: AbortSignal.timeout(2000) });
      return i + 1;
    } catch (e) {
      if (proc && proc.exitCode !== null) {
        return -1;
      }
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
  return -1;
}

function stopProc(proc) {
  if (!proc || proc.exitCode !== null) return;
  spawnSync('taskkill', ['/PID', String(proc.pid), '/T', '/F'], { stdio: 'ignore' });
}

/**
 * [批次3.5] 本地 LLM stub：按请求体 model 字段决定行为，并统计各 model 的调用次数。
 * 用于验证降级链，不依赖真实供应商与密钥（端口由系统分配，避免冲突）。
 *   stub-500 → 500（可降级）   stub-401 → 401（不可降级）   其他 → 合法成功响应
 */
function startLlmStub() {
  return new Promise((resolve) => {
    const counts = {};
    const server = http.createServer((req, res) => {
      let raw = '';
      req.on('data', (chunk) => { raw += chunk; });
      req.on('end', () => {
        let model = '';
        try {
          model = JSON.parse(raw).model || '';
        } catch (e) {
          model = '';
        }
        counts[model] = (counts[model] || 0) + 1;

        const send = (status, payload) => {
          res.writeHead(status, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(payload));
        };
        if (model === 'stub-500') {
          send(500, { error: { message: 'stub upstream failure' } });
          return;
        }
        if (model === 'stub-401') {
          send(401, { error: { message: 'stub unauthorized' } });
          return;
        }
        // 含 fenced 代码块：走通 LlmClient 解析与代码提取，避免被判为「输出无效」而触发同模型重试
        send(200, {
          choices: [{
            message: { content: '```latex\n\\begin{tikzpicture}\n\\draw (0,0) -- (1,1);\n\\end{tikzpicture}\n```' },
            finish_reason: 'stop',
          }],
          usage: { prompt_tokens: 10, completion_tokens: 20, total_tokens: 30 },
        });
      });
    });
    server.listen(0, '127.0.0.1', () => {
      resolve({ port: server.address().port, counts, close: () => server.close() });
    });
  });
}

/**
 * [批次3.5] 启动指向本地 stub 的临时后端实例。
 * 用命令行属性注入 LLM 配置：Spring 中命令行参数优先级高于配置文件（含 .env 导入），必然覆盖生效。
 */
function startTempBackend({ port, qwenModel, sfModel, stubPort, queueCapacity }) {
  const stubBase = `http://127.0.0.1:${stubPort}/v1`;
  const args = [
    '-jar', JAR,
    `--server.port=${port}`,
    '--app.rag.enabled=false',
    '--app.llm.qwen.enabled=true',
    `--app.llm.qwen.api-url=${stubBase}`,
    '--app.llm.qwen.api-key=stub-key',
    `--app.llm.qwen.model=${qwenModel}`,
    '--app.llm.siliconflow.enabled=true',
    `--app.llm.siliconflow.api-url=${stubBase}`,
    '--app.llm.siliconflow.api-key=stub-key',
    `--app.llm.siliconflow.model=${sfModel}`,
    '--app.llm.deepseek.enabled=false',
  ];
  // [批次3.6] 仅在显式传入时注入队列容量，避免影响降级链用例的默认行为
  if (queueCapacity !== undefined) {
    args.push(`--app.compile.queue-capacity=${queueCapacity}`);
  }
  const outFd = fs.openSync(path.join(ROOT, `verify-fallback-${port}.log`), 'w');
  const errFd = fs.openSync(path.join(ROOT, `verify-fallback-${port}-err.log`), 'w');
  return spawn(findJava(), args, { stdio: ['ignore', outFd, errFd] });
}

/** [批次3.6] 从 api-stats 的 errorTypes 取指定分类计数（不存在返回 0）。 */
function countErrorType(json, errorType) {
  const list = (json && json.data && json.data.errorTypes) || [];
  const hit = list.find((it) => it.error_type === errorType);
  return hit ? hit.count : 0;
}

async function main() {
  let proc = null;
  const tmpCsv = path.join(ROOT, 'verify-tmp.csv');

  if (!noStart) {
    const java = findJava();
    if (!java) {
      console.log('[ERROR] 未找到 JDK 17+，请先运行 build.cmd');
      process.exit(1);
    }
    if (!fs.existsSync(JAR)) {
      console.log('[ERROR] 未找到 jar，请先运行 build.cmd');
      process.exit(1);
    }
    console.log(`[INFO] 启动后端: ${java} -jar ${JAR}`);
    const outFd = fs.openSync(path.join(ROOT, 'verify-app.log'), 'w');
    const errFd = fs.openSync(path.join(ROOT, 'verify-app-err.log'), 'w');
    proc = spawn(java, ['-jar', JAR], { stdio: ['ignore', outFd, errFd] });

    const waited = await waitReady(proc, 60);
    if (waited < 0) {
      console.log('[ERROR] 后端 60s 内未就绪，请查看 verify-app.log / verify-app-err.log');
      stopProc(proc);
      process.exit(1);
    }
    console.log(`[INFO] 后端已就绪（约 ${waited}s）`);
  }

  // ---------------- 1. 鉴权 ----------------
  log('1. 鉴权');
  const noToken = await fetch(`${BASE}/api/notice`).catch(() => null);
  check('无 token 访问受保护接口返回 401', noToken && noToken.status === 401, `实际=${noToken && noToken.status}`);

  const login = await req('POST', `${BASE}/api/auth/admin/login`, {
    body: { adminAccount: 'admin123', password: '666666' },
  });
  const token = login.json && login.json.data && login.json.data.token;
  check('管理员登录返回 data.{user,token}', Boolean(token), login.json ? login.json.message : '无响应');
  if (!token) {
    throw new Error('管理员登录失败，后续用例终止（请确认库 X 中存在 admin123/666666）');
  }

  const badToken = await fetch(`${BASE}/api/admin/static`, { headers: { Authorization: 'Bearer invalid.token.xx' } });
  check('非法 token 访问 /api/admin/** 被拒', badToken.status === 401 || badToken.status === 403, `实际=${badToken.status}`);

  // ---------------- 2. 管理后台 ----------------
  log('2. 管理后台');
  const staticRes = await req('GET', `${BASE}/api/admin/static`, { token });
  check('/api/admin/static 返回计数', staticRes.json && staticRes.json.success && staticRes.json.data.users !== undefined);

  const users = await req('GET', `${BASE}/api/admin/users?page=1&pageSize=2`, { token });
  check('/api/admin/users 分页返回', users.json && users.json.success && users.json.data.pagination.totalPages >= 1);

  const notices = await req('GET', `${BASE}/api/admin/notices?page=1&pageSize=2`, { token });
  check('/api/admin/notices 列表+统计', notices.json && notices.json.success && notices.json.data.statistics);

  const noticeStat = await req('GET', `${BASE}/api/admin/notices/statistics/overview`, { token });
  check('/api/admin/notices/statistics/overview', noticeStat.json && noticeStat.json.success, noticeStat.json && noticeStat.json.message);

  const userStat = await req('GET', `${BASE}/api/admin/users/statistics/overview`, { token });
  check('/api/admin/users/statistics/overview（GROUP BY 修复）', userStat.json && userStat.json.success, userStat.json && userStat.json.message);

  const tables = await req('GET', `${BASE}/api/admin/log/tables-status`, { token });
  check('/api/admin/log/tables-status', tables.json && tables.json.success && tables.json.data.users !== undefined);

  const apiStats = await req('GET', `${BASE}/api/admin/log/api-stats`, { token });
  check('/api/admin/log/api-stats', apiStats.json && apiStats.json.success && apiStats.json.data.summary);

  // [O1] responseTime 由真实 api_log.duration_ms 聚合（无数据时 sample_count=0、avg/p95 为 null）
  const responseTime = apiStats.json && apiStats.json.data && apiStats.json.data.responseTime;
  check('/api/admin/log/api-stats 返回真实耗时聚合 responseTime',
    Boolean(responseTime) && Number.isFinite(responseTime.sample_count),
    `responseTime=${JSON.stringify(responseTime)}`);

  // [E2] 失败分类计数；无数据时为 []，故只断言类型（不依赖真实数据）
  const errorTypes = apiStats.json && apiStats.json.data && apiStats.json.data.errorTypes;
  check('/api/admin/log/api-stats 返回失败分类计数 errorTypes',
    Array.isArray(errorTypes),
    `errorTypes=${JSON.stringify(errorTypes)}`);

  // [E3] 按日耗时序列；无数据时为 []，同理只断言类型与元素字段
  const timeSeriesRt = apiStats.json && apiStats.json.data && apiStats.json.data.responseTimeSeries;
  check('/api/admin/log/api-stats 返回按日耗时序列 responseTimeSeries',
    Array.isArray(timeSeriesRt)
      && timeSeriesRt.every((it) => it && it.date !== undefined && it.avg !== undefined
        && it.p95 !== undefined && it.count !== undefined),
    `responseTimeSeries=${JSON.stringify(timeSeriesRt)}`);

  const sysLogs = await req('GET', `${BASE}/api/admin/log/system-logs?page=1&pageSize=2`, { token });
  check('/api/admin/log/system-logs 分页', sysLogs.json && sysLogs.json.success && sysLogs.json.data.pagination);

  const health = await req('GET', `${BASE}/api/admin/log/health-overview`, { token });
  check('/api/admin/log/health-overview', health.json && health.json.success && health.json.data.systemStatus);

  // ---------------- 3. 用户侧查询 ----------------
  log('3. 用户侧查询');
  const noticeList = await req('GET', `${BASE}/api/notice`, { token });
  check('/api/notice 统一 success 结构', noticeList.json && noticeList.json.success && noticeList.json.data.notices);

  const unread = await req('GET', `${BASE}/api/notice/unread-count`, { token });
  check('/api/notice/unread-count', unread.json && unread.json.success && unread.json.data.unreadCount !== undefined);

  const validate = await req('GET', `${BASE}/api/auth/validate`, { token });
  check('/api/auth/validate 返回 data.user', validate.json && validate.json.success && validate.json.data.user.user_id !== undefined);

  const history = await req('GET', `${BASE}/api/history?page=1&limit=2`, { token });
  check('/api/history 分页 records', history.json && history.json.success && Array.isArray(history.json.data.records));

  const histStats = await req('GET', `${BASE}/api/history/stats/summary`, { token });
  check('/api/history/stats/summary', histStats.json && histStats.json.success && histStats.json.data.total_count !== undefined);

  const conv = await req('GET', `${BASE}/api/conversations`, { token });
  check('/api/conversations', conv.json && conv.json.success);

  const fb = await req('GET', `${BASE}/api/feedback?page=1&limit=2`, { token });
  check('/api/feedback 管理员列表 records', fb.json && fb.json.success && Array.isArray(fb.json.data.records));

  const myFb = await req('GET', `${BASE}/api/feedback/user/my-feedbacks?page=1&limit=2`, { token });
  check('/api/feedback/user/my-feedbacks', myFb.json && myFb.json.success && Array.isArray(myFb.json.data.records));

  // ---------------- 4. 数据集 ----------------
  log('4. 数据集（上传/列表/改名/下载/删除）');
  fs.writeFileSync(tmpCsv, 'a,b\n1,2\n', 'utf8');

  const form = new FormData();
  form.append('name', 'verify-test');
  form.append('description', 'verify-csv');
  form.append('file', new Blob(['a,b\n1,2\n'], { type: 'text/csv' }), 'verify-tmp.csv');
  const uploadRes = await fetch(`${BASE}/api/datasets`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
    signal: AbortSignal.timeout(20000),
  });
  const uploadJson = await uploadRes.json().catch(() => null);
  const newId = uploadJson && uploadJson.data && uploadJson.data.data_id;
  check('数据集上传返回 DatasetVO', Boolean(newId) && uploadJson.success, JSON.stringify(uploadJson));

  const dsList = await req('GET', `${BASE}/api/datasets`, { token });
  check('数据集列表包含新上传项', dsList.json && dsList.json.success && dsList.json.data.some((d) => d.data_id === newId));

  const dsUpd = await req('POST', `${BASE}/api/datasets/${newId}/update`, {
    token,
    body: { name: 'verify-renamed', description: 'verify-csv' },
  });
  check('数据集改名成功', dsUpd.json && dsUpd.json.success && dsUpd.json.data.data_name === 'verify-renamed');

  const dlRes = await fetch(`${BASE}/api/datasets/download/${newId}`, { headers: { Authorization: `Bearer ${token}` } });
  const dlBuf = Buffer.from(await dlRes.arrayBuffer());
  check('数据集下载返回文件', dlRes.status === 200 && dlBuf.length > 0, `http=${dlRes.status} size=${dlBuf.length}`);

  const dsDel = await req('DELETE', `${BASE}/api/datasets/${newId}`, { token });
  check('数据集删除成功', dsDel.json && dsDel.json.success);

  // ---------------- 5. XeLaTeX 编译链路（G1 异步契约） ----------------
  log('5. XeLaTeX 编译链路');
  const records = (history.json && history.json.data.records) || [];
  if (records.length > 0) {
    const hid = records[0].history_id;
    const submitted = await req('POST', `${BASE}/api/compile/${hid}`, { token, timeout: 15000 });
    const taskId = submitted.json && submitted.json.data && submitted.json.data.task_id;
    if (submitted.json && submitted.json.success && taskId) {
      // 轮询终态：1.5s 间隔，最多 40 次（60s，覆盖 30s 编译超时 + 排队余量）
      let finalTask = null;
      for (let i = 0; i < 40; i++) {
        await new Promise((r) => setTimeout(r, 1500));
        const poll = await req('GET', `${BASE}/api/compile/task/${taskId}`, { token, timeout: 15000 });
        const t = poll.json && poll.json.data;
        if (t && (t.status === 'success' || t.status === 'failed')) { finalTask = t; break; }
      }
      if (finalTask && finalTask.status === 'success') {
        check(`编译 history_id=${hid} 任务终态 success 且含 pdf_path`, Boolean(finalTask.pdf_path));
        check(`任务含耗时 duration_ms`, Number.isFinite(finalTask.duration_ms));
        const pdfRes = await fetch(`${BASE}/api/compile/${hid}/pdf`, { headers: { Authorization: `Bearer ${token}` } });
        check('PDF 归属鉴权流式返回 200', pdfRes.status === 200, `实际=${pdfRes.status}`);
      } else {
        warnMsg(`编译任务未成功（history_id=${hid}）`,
          `终态=${finalTask ? finalTask.status : '轮询超时'}；可能未安装 XeLaTeX；error=${finalTask && finalTask.error}`);
      }
    } else {
      warnMsg('编译任务提交失败', `详情=${submitted.json && submitted.json.message}`);
    }
  } else {
    warnMsg('编译用例跳过', '当前用户暂无历史记录');
  }

  // ---------------- 6. 降级链（[批次3.5] 本地 stub，不依赖真实供应商与密钥） ----------------
  log('6. 降级链（stub 注入可降级 / 不可降级错误）');
  const stub = await startLlmStub();
  let procDegradable = null;
  let procFatal = null;
  try {
    // 用例 A：主通道 500（可降级）→ 应接力到 siliconflow 并成功，且响应标注实际完成通道
    procDegradable = startTempBackend({ port: 3100, qwenModel: 'stub-500', sfModel: 'stub-ok', stubPort: stub.port });
    const readyA = await waitReady(procDegradable, 60, 'http://localhost:3100');
    if (readyA < 0) {
      check('降级链用例A：临时实例就绪', false, '60s 内未就绪，详见 verify-fallback-3100.log');
    } else {
      const okBeforeA = stub.counts['stub-ok'] || 0;
      const chatA = await req('POST', 'http://localhost:3100/api/chat', {
        token,
        body: { message: '画一个简单的折线图' },
        timeout: 90000,
      });
      check('用例A：主通道 500（可降级）→ 生成仍成功',
        chatA.status === 200 && Boolean(chatA.json && chatA.json.success),
        `http=${chatA.status} body=${JSON.stringify(chatA.json)}`);
      const usedA = chatA.json && chatA.json.data && chatA.json.data.model_used;
      check('用例A：响应标注实际完成通道 model_used=siliconflow', usedA === 'siliconflow', `model_used=${usedA}`);
      check('用例A：备用通道确实被调用一次',
        (stub.counts['stub-ok'] || 0) === okBeforeA + 1, `stub-ok=${stub.counts['stub-ok'] || 0}`);

      // 用例 C（回归防护）：不传 model 时必须回落主通道 qwen，而不是抛 NPE
      const qwen500BeforeC = stub.counts['stub-500'] || 0;
      const chatC = await req('POST', 'http://localhost:3100/api/chat', {
        token,
        body: { message: '画一个简单的折线图' },
        timeout: 90000,
      });
      check('用例C：未传 model → 回落主通道 qwen（不抛 NPE）',
        chatC.status === 200 && Boolean(chatC.json && chatC.json.success),
        `http=${chatC.status} body=${JSON.stringify(chatC.json)}`);
      check('用例C：请求确实打到了 qwen 槽位',
        (stub.counts['stub-500'] || 0) === qwen500BeforeC + 1, `stub-500=${stub.counts['stub-500'] || 0}`);
    }

    // 用例 B：主通道 401（不可降级）→ 应直接报错，不得消耗备用通道
    procFatal = startTempBackend({ port: 3110, qwenModel: 'stub-401', sfModel: 'stub-ok', stubPort: stub.port });
    const readyB = await waitReady(procFatal, 60, 'http://localhost:3110');
    if (readyB < 0) {
      check('降级链用例B：临时实例就绪', false, '60s 内未就绪，详见 verify-fallback-3110.log');
    } else {
      const okBeforeB = stub.counts['stub-ok'] || 0;
      const fatalBeforeB = stub.counts['stub-401'] || 0;
      const chatB = await req('POST', 'http://localhost:3110/api/chat', {
        token,
        body: { message: '画一个简单的折线图' },
        timeout: 90000,
      });
      check('用例B：主通道 401（不可降级）→ 请求失败', chatB.status >= 400, `http=${chatB.status}`);
      check('用例B：主通道被调用一次',
        (stub.counts['stub-401'] || 0) === fatalBeforeB + 1, `stub-401=${stub.counts['stub-401'] || 0}`);
      check('用例B：未降级到备用通道（确定性错误不重试）',
        (stub.counts['stub-ok'] || 0) === okBeforeB,
        `stub-ok 增量=${(stub.counts['stub-ok'] || 0) - okBeforeB}`);
    }
  } finally {
    stopProc(procDegradable);
    stopProc(procFatal);
    stub.close();
  }

  // ---------------- 7. 编译队列满（[批次3.6] 容量注入，验证「任务不被静默丢弃」） ----------------
  log('7. 编译队列满（注入 queue-capacity=1 + 并发提交）');
  const queueHistoryId = ((history.json && history.json.data && history.json.data.records) || [])
    .map((r) => r.history_id)[0];
  if (!queueHistoryId) {
    warnMsg('编译队列满用例跳过', '当前用户暂无历史记录，无法构造编译请求');
  } else {
    const stubQueue = await startLlmStub();
    const procQueue = startTempBackend({
      port: 3120,
      qwenModel: 'stub-ok',
      sfModel: 'stub-ok',
      stubPort: stubQueue.port,
      queueCapacity: 1,
    });
    try {
      const readyQ = await waitReady(procQueue, 60, 'http://localhost:3120');
      if (readyQ < 0) {
        check('编译队列满：临时实例就绪', false, '60s 内未就绪，详见 verify-fallback-3120.log');
      } else {
        const statsBefore = await req('GET', `${BASE}/api/admin/log/api-stats`, { token });
        const queueFullBefore = countErrorType(statsBefore.json, 'COMPILE_QUEUE_FULL');

        // 容量 = maxPoolSize(4) + queue(1) = 5：同一时刻压入 12 个请求，必然有被拒的
        const submissions = await Promise.all(
          Array.from({ length: 12 }, () => req('POST', `http://localhost:3120/api/compile/${queueHistoryId}`, {
            token,
            timeout: 15000,
          })),
        );
        const rejected = submissions.filter((r) => r.status === 503);
        check('编译队列满：并发提交确实触发 503（任务未被静默丢弃）', rejected.length > 0,
          `状态码=${submissions.map((r) => r.status).join(',')}`);
        check('编译队列满：503 带明确提示文案',
          rejected.some((r) => r.json && r.json.message && r.json.message.includes('队列已满')),
          `message=${rejected.map((r) => (r.json && r.json.message) || '').join(' | ')}`);

        const statsAfter = await req('GET', `${BASE}/api/admin/log/api-stats`, { token });
        const queueFullAfter = countErrorType(statsAfter.json, 'COMPILE_QUEUE_FULL');
        check('编译队列满：被拒任务已落库 COMPILE_QUEUE_FULL',
          queueFullAfter > queueFullBefore,
          `before=${queueFullBefore} after=${queueFullAfter}`);
      }
    } finally {
      stopProc(procQueue);
      stubQueue.close();
    }
  }

  // ---------------- 8. 邮件验证码 ----------------
  log('8. 邮件验证码（需人工确认）');
  console.log('  [SKIP] 发送验证码会真实发信，未自动执行。');
  console.log('         手动验证（把 <你的邮箱> 换成真实待注册邮箱）：');
  console.log(`         curl -X POST ${BASE}/api/verification/send-register-code -H "Content-Type: application/json" -d "{\\"email\\":\\"<你的邮箱>\\"}"`);
}

(async () => {
  let exitCode = 0;
  try {
    await main();
  } catch (e) {
    fail += 1;
    console.log(`[ERROR] 验证过程异常: ${e.message}`);
  }

  for (const f of ['verify-tmp.csv', 'verify-tmp-download.csv']) {
    try {
      fs.unlinkSync(path.join(ROOT, f));
    } catch (e) {
      // ignore
    }
  }

  console.log('\n================ 结果 ================');
  console.log(`  PASS=${pass}  FAIL=${fail}  WARN=${warn}`);
  if (fail === 0) {
    for (const f of ['verify-app.log', 'verify-app-err.log',
      'verify-fallback-3100.log', 'verify-fallback-3100-err.log',
      'verify-fallback-3110.log', 'verify-fallback-3110-err.log',
      'verify-fallback-3120.log', 'verify-fallback-3120-err.log']) {
      try {
        fs.unlinkSync(path.join(ROOT, f));
      } catch (e) {
        // ignore
      }
    }
    console.log('  全部通过。');
  } else {
    exitCode = 1;
    console.log('  存在失败项，日志保留于 verify-app.log / verify-app-err.log');
  }
  process.exit(exitCode);
})();
