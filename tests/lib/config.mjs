// 测试套件公共配置：路径、服务地址、超时、凭据与命令行参数解析。
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** 仓库根（hello/） */
export const ROOT = path.resolve(__dirname, '..', '..');
/** 测试工具根（hello/tests/） */
export const TESTS_DIR = path.resolve(__dirname, '..');
export const DOCS_DIR = path.join(ROOT, 'docs');
export const TESTDATA_DIR = path.join(DOCS_DIR, 'testdata');

/** 后端 / 前端地址（可用 PG_API_BASE / PG_WEB_BASE 覆盖） */
export const API_BASE = process.env.PG_API_BASE || 'http://localhost:3000';
export const WEB_BASE = process.env.PG_WEB_BASE || 'http://localhost:8080';

/** 单例超时（毫秒） */
export const TIMEOUTS = {
  NAV: 30000,
  UPLOAD: 60000,
  GENERATE: Number(process.env.PG_GENERATE_TIMEOUT_MS || 180000),
  COMPILE: Number(process.env.PG_COMPILE_TIMEOUT_MS || 60000),
  HTTP: Number(process.env.PG_HTTP_TIMEOUT_MS || 30000),
};

export function todayStamp(now = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`;
}

export function nowStamp(now = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${todayStamp(now)}_${p(now.getHours())}${p(now.getMinutes())}${p(now.getSeconds())}`;
}

export const RESULTS_DIR = path.join(TESTS_DIR, 'results', todayStamp());
export const REPORT_PATH = path.join(DOCS_DIR, `manual-test-report-${todayStamp()}.md`);

/**
 * 读取测试账号。凭据仅从环境变量注入，绝不落盘。
 * @returns {{email: string, password: string}}
 */
export function getCredentials() {
  const email = process.env.TEST_EMAIL;
  const password = process.env.TEST_PASSWORD;
  if (!email || !password) {
    throw new Error(
      '缺少测试账号：请先设置环境变量 TEST_EMAIL 与 TEST_PASSWORD（凭据不会被写入任何文件）。'
    );
  }
  return { email, password };
}

/** 极简参数解析：--key value / --key=value / --flag */
export function parseArgs(argv = process.argv.slice(2)) {
  const args = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) {
      args._.push(token);
      continue;
    }
    const body = token.slice(2);
    const eq = body.indexOf('=');
    if (eq !== -1) {
      args[body.slice(0, eq)] = body.slice(eq + 1);
    } else if (argv[i + 1] && !argv[i + 1].startsWith('--')) {
      args[body] = argv[++i];
    } else {
      args[body] = true;
    }
  }
  return args;
}
