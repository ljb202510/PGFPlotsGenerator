// 由 results.json 生成人读测试报告（Markdown）。
import { CASES } from './cases.mjs';
import { relFromRoot } from './artifacts.mjs';

const RESULT_LABEL = {
  pass: '通过',
  fail: '失败',
  suspect: '存疑',
  skipped: '未执行',
};

function mark(v) {
  return v ? '✓' : '';
}

// 证据链接：results.json 中存的是 hello/ 相对路径；报告位于 docs/，需补 ../ 前缀。
function evidenceCell(ev) {
  if (!ev) return '';
  const link = (p) => `../${p}`;
  const items = [];
  if (ev.png) items.push(`[渲染图](${link(ev.png)})`);
  if (ev.pdf) items.push(`[PDF](${link(ev.pdf)})`);
  if (ev.screenshot) items.push(`[聊天截图](${link(ev.screenshot)})`);
  if (ev.dialogScreenshot) items.push(`[预览截图](${link(ev.dialogScreenshot)})`);
  if (ev.chartCode) items.push(`[代码](${link(ev.chartCode)})`);
  return items.join(' ');
}

/**
 * @param {object} meta 运行元信息
 * @param {Array} results 逐例结果
 */
export function buildReport(meta, results) {
  const lines = [];
  lines.push(`# PGFPlotsGenerator 核心功能自动实测报告（${meta.date}）`);
  lines.push('');
  lines.push('> 本报告由 `hello/tests/run-manual-cases.mjs` 自动生成，对应用例见 `docs/manual-test-cases.md` §5/§6（01–10）。');
  lines.push('');
  lines.push('## 1. 运行环境与方法');
  lines.push('');
  lines.push('| 项 | 值 |');
  lines.push('|---|---|');
  lines.push(`| 后端地址 | \`${meta.apiBase}\` |`);
  lines.push(`| 前端地址 | \`${meta.webBase}\` |`);
  lines.push(`| UI 通道 | ${meta.uiChannel} |`);
  lines.push(`| 兜底通道 | ${meta.apiFallback} |`);
  lines.push(`| 默认模型 | ${meta.defaultModel}${meta.retryModel ? `（失败自动切 ${meta.retryModel} 重试一次）` : ''} |`);
  lines.push(`| 用例范围 | ${meta.scope} |`);
  lines.push(`| 开始时间 | ${meta.startedAt} |`);
  lines.push(`| 结束时间 | ${meta.finishedAt} |`);
  lines.push(`| 耗时 | ${meta.durationText} |`);
  lines.push('');
  lines.push('## 2. 结果总览');
  lines.push('');
  lines.push('| 用例 | 验证场景 | 图表类型 | 是否上传 | AI 生成 | 代码生成 | PDF 编译 | PDF 渲染 | 结果 | 模型 | 备注 |');
  lines.push('|:----:|----------|----------|:----:|:----:|:----:|:----:|:----:|:----:|:----:|------|');
  for (const r of results) {
    const c = CASES.find((x) => x.id === r.id) || {};
    const model = r.model === 'deepseek' ? 'Deepseek' : r.model === 'qwen' ? 'Qwen' : '-';
    lines.push(
      `| ${r.id} | ${c.chart || ''} | ${c.chart || ''} | ${mark(r.uploaded)} | ${mark(
        r.aiGenerated
      )} | ${mark(r.codeGenerated)} | ${mark(r.pdfCompiled)} | ${mark(r.pdfRendered)} | ${
        RESULT_LABEL[r.result] || r.result
      } | ${model}${r.retried ? '（重试）' : ''} | ${cellText(r.note)} |`
    );
  }
  lines.push('');
  const counts = results.reduce((acc, r) => {
    acc[r.result] = (acc[r.result] || 0) + 1;
    return acc;
  }, {});
  lines.push(
    `> 统计：通过 ${counts.pass || 0} / 失败 ${counts.fail || 0} / 存疑 ${counts.suspect || 0} / 未执行 ${
      counts.skipped || 0
    }，共 ${results.length} 例。`
  );
  lines.push('');
  lines.push('## 3. 逐例详情');
  lines.push('');
  for (const r of results) {
    const c = CASES.find((x) => x.id === r.id) || {};
    lines.push(`### 用例 ${r.id}：${c.chart || ''}（${RESULT_LABEL[r.result] || r.result}）`);
    lines.push('');
    lines.push(`- **数据文件**：${c.upload ? '`' + c.file + '`' : '无（不上传）'}`);
    lines.push(`- **数据预览**：${c.dataPreview || '-'}`);
    lines.push(`- **失败环节**：${r.stage && r.stage !== '-' ? r.stage : '无'}${r.retried ? '（已重试）' : ''}`);
    lines.push(`- **证据**：${evidenceCell(r.evidence) || '无'}`);
    if (r.errorText) {
      lines.push('- **错误信息**：');
      lines.push('  ```');
      lines.push(
        String(r.errorText)
          .split('\n')
          .slice(0, 12)
          .map((l) => '  ' + l)
          .join('\n')
      );
      lines.push('  ```');
    }
    if (r.checks && r.checks.length) {
      lines.push('- **验证要点（需结合 PDF/截图核对）**：');
      for (const ck of r.checks) lines.push(`  - ${ck}`);
    }
    lines.push('');
  }
  lines.push('## 4. 说明与限制');
  lines.push('');
  lines.push('- 机器判定：AI 返回的 `chart_code` 非空且可编译出 PDF，即计入「代码生成/PDF 编译」通过。');
  lines.push('- 视觉判定：图表是否“符合语义”（标题/轴标签中文、数据一致、量级单位匹配、标注与图例不遮挡）需**人工查看上方证据图片**确认，报告不代替人眼结论。');
  lines.push('- 若失败样本已自动切换备用模型重试并成功，备注标注「重试」。');
  lines.push('- 截图可能包含当前登录用户名，仅供本机核查，请勿将 `tests/results/` 提交到公开仓库。');
  lines.push('');
  return lines.join('\n');
}

function cellText(s) {
  return String(s || '').replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

export function formatDuration(ms) {
  const s = Math.round(ms / 1000);
  const m = Math.floor(s / 60);
  return m > 0 ? `${m} 分 ${s % 60} 秒` : `${s} 秒`;
}
