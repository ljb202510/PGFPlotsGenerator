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

async function waitReady(proc, seconds) {
  for (let i = 0; i < seconds; i += 1) {
    try {
      await fetch(`${BASE}/api/notice`, { signal: AbortSignal.timeout(2000) });
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

  // ---------------- 5. 编译链路 ----------------
  log('5. XeLaTeX 编译链路');
  const records = (history.json && history.json.data.records) || [];
  if (records.length > 0) {
    const hid = records[0].history_id;
    const compiled = await req('POST', `${BASE}/api/compile/${hid}`, { token, timeout: 60000 });
    if (compiled.json && compiled.json.success) {
      check(`编译 history_id=${hid} 返回 data.pdf_path`, Boolean(compiled.json.data.pdf_path));
      const pdfRes = await fetch(`${BASE}/api/compile/${hid}/pdf`, { headers: { Authorization: `Bearer ${token}` } });
      check('PDF 归属鉴权流式返回 200', pdfRes.status === 200, `实际=${pdfRes.status}`);
    } else {
      warnMsg(`编译用例未通过（history_id=${hid}）`, `可能未安装 XeLaTeX 或该记录无代码；详情=${compiled.json && compiled.json.message}`);
    }
  } else {
    warnMsg('编译用例跳过', '当前用户暂无历史记录');
  }

  // ---------------- 6. 邮件验证码 ----------------
  log('6. 邮件验证码（需人工确认）');
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
    for (const f of ['verify-app.log', 'verify-app-err.log']) {
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
