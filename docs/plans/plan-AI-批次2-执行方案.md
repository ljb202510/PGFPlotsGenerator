# 批次 2 可执行执行方案（G1 编译队列化 + G2 并发控制 + O1 耗时拆解）

> **本文档是自包含的施工图**：执行者拿到本文档即可直接动工，无需回读 `plan-AI.md`。
> 项目：PGFPlotsGenerator 后端（`hello/spring-backend/`）+ 前端（`hello/src/`，Vue 3.2 + element-plus + axios）
> 前置：批次1 已完成并验收，基线 **PASS=27 / FAIL=0**。日期：2026-09-13
> **范围**：仅 G1 / G2 / O1。**明确不做**：compile_task 表持久化（演进版）、SSE、A4、G3/G4/O2/O4、E1（T12 已留给 Trae）。

---

## 0. 硬性约束（先读，违反即返工）

1. **契约变更三处一次到位**：后端端点 + `verify.js` 第 5 段 + 前端两处 `compileToPDF` 是同一个契约，禁止半改造状态。
2. **编译任务不可静默丢弃**：新线程池拒绝策略 = 任务标记 failed；禁止复用 `ragIndexExecutor`。
3. **归属校验**：`GET /api/compile/task/{taskId}` 必须校验 userId 防越权。
4. **重启丢任务是已知取舍**：查不到的 taskId 返回 404 语义，错误信息写明「服务重启会清空任务状态，请重新提交编译」。
5. `LatexCompiler` 校验/预处理/超时销毁逻辑不动（`validate` 参数化是唯一例外，见 §3.3）。
6. 每任务节 `mvn -q -DskipTests compile`；每大节 `scripts\build.cmd && scripts\verify.cmd`（27 全绿）。
7. 风格：`@RequiredArgsConstructor`、`[TAG]` 中文日志、`Result` 统一响应、迁移 SQL 放 `hello/migrations/`、`${ENV:default}`。

---

## 1. 前置检查（T0）

| # | 检查项 | 通过标准 |
|---|---|---|
| 1 | `scripts\build.cmd && scripts\verify.cmd` | PASS=27 FAIL=0 WARN=0 |
| 2 | MySQL 可达（root/000） | 连接成功 |
| 3 | `xelatex --version` | 有版本号 |
| 4 | `SHOW COLUMNS FROM X.api_log LIKE 'duration_ms';` | 空（未迁移） |

---

## 2. 数据库迁移（任务 T1，O1 用）

新建 `hello/migrations/alter_api_log_duration_ms.sql`：

```sql
-- 批次2/O1：api_log 记录 LLM 调用耗时（毫秒），由 AdminLogService 聚合 avg/P95
USE `X`;

ALTER TABLE api_log
  ADD COLUMN duration_ms BIGINT NULL AFTER prompt_version;
```

同步 **`entity/ApiLog.java`** [MODIFY]：

```java
/** LLM 调用耗时（毫秒），批次2/O1；编译耗时记在编译任务终态，不落 api_log */
private Long durationMs;
```

**自检**：列存在；`mvn -q -DskipTests compile` 通过。

---

## 3. 配置层（任务 T2）

### 3.1 `config/AppProperties.java` [MODIFY]——`Latex` 嵌套类新增

```java
/** XeLaTeX 最大并发编译数（批次2/G2，Semaphore 限流） */
private int maxConcurrency = 2;
```

### 3.2 `application.yml` [MODIFY]——`app.latex` 节点追加

```yaml
    max-concurrency: ${LATEX_MAX_CONCURRENCY:2}
```

### 3.3 顺手清理：validate 上限参数化

`LatexCompiler.validate(String)` 硬编码 `MAX_CODE_LENGTH=50000`，而 `app.latex.maxCodeLength` 配置从未被消费。改为增加重载：

```java
/** 上限可配置的校验（批次2/T2）；原无参重载保留并委托 validate(code, 50000) 向后兼容 */
public static Validation validate(String code, int maxCodeLength) {
    // 原逻辑不变，仅把 MAX_CODE_LENGTH 替换为 maxCodeLength 参数
}
```

`CompileService` L80-85 调用处改为 `LatexCompiler.validate(processed, appProperties.getLatex().getMaxCodeLength())`。

**自检**：编译通过；`scripts\verify.cmd` 27 绿。

---

## 4. 任务清单总表

| 任务 | 内容 | 主要落点 | 依赖 |
|---|---|---|---|
| T0 | 前置环境检查 | — | — |
| T1 | `api_log.duration_ms` 迁移 + 实体字段 | `hello/migrations/`、`entity/ApiLog` | T0 |
| T2 | 配置层（max-concurrency + validate 参数化） | `AppProperties`、`application.yml`、`LatexCompiler` | T0 |
| T3 | G1 后端：CompileTaskService + AsyncConfig 新池 | `service/CompileTaskService`、`config/AsyncConfig` | T2 |
| T4 | G1 契约：CompileController 两端点 | `controller/CompileController` | T3 |
| T5 | O1：ChatService 计时 + AdminLogService 真实聚合 | `ChatService`、`AdminLogService`、`AdminMapper.xml` | T1 |
| T6 | G1 前端：compile.js + 两视图轮询改造 | `src/utils/compile.js`、`ChartGenerator.vue`、`MyHistory.vue` | T4 |
| T7 | verify.js 第 5 段按新契约改造 | `spring-backend/scripts/verify.js` | T4 |
| T8 | 三处联动联调 + 总验收 + 文档回填 | 全部 | T5/T6/T7 |

---

## 5. G1 编译任务队列化（T3、T4、T6、T7）

### 5.1 T3：`config/AsyncConfig.java` [MODIFY]——新增编译专用线程池

在现有 `AsyncConfig` 中追加（保留 ragIndexExecutor 不动）：

```java
/** [G1] 编译任务线程池：编译不可丢弃，拒绝时直接标记任务 failed（在 CompileTaskService 内处理） */
@Bean("compileTaskExecutor")
public ThreadPoolTaskExecutor compileTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("compile-task-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
}
```

> 说明：池 max=4 + 队列 100 已经是第一道限流；真正的 XeLaTeX 进程并发由 G2 的 Semaphore 控制（permits 默认 2）。池满拒绝的场景在 `submit` 同步段捕获（见 5.2 的 TaskRejectedException）。

### 5.2 T3：`service/CompileTaskService.java` [NEW]（核心类，完整骨架）

```java
package com.pg.pgfplots.service;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * [G1] 编译异步任务服务（批次2）：内存任务表 + 独立线程池。
 * POST 提交立即返回 task_id；GET 轮询终态。已知取舍：重启丢失任务状态（演进版 compile_task 表不在本批）。
 * [G2] 工作线程内 Semaphore 限流 XeLaTeX 进程并发（permits = app.latex.max-concurrency）。
 */
@Slf4j
@Service
public class CompileTaskService {

    /** status: queued | running | success | failed（后两者终态） */
    public record CompileTask(String taskId, Integer userId, Integer historyId, String status,
                              String pdfPath, Long fileSize, String error, Long durationMs,
                              LocalDateTime createdAt) {}

    private static final int MAX_TASKS = 500;

    private final Map<String, CompileTask> tasks = new ConcurrentHashMap<>();
    private final Semaphore xelatexPermits;
    private final CompileService compileService;
    private final AppProperties appProperties;
    private final SystemLogWriter systemLogWriter;
    private final ThreadPoolTaskExecutorHolder executorHolder;

    public CompileTaskService(CompileService compileService, AppProperties appProperties,
                              SystemLogWriter systemLogWriter,
                              org.springframework.beans.factory.ObjectProvider<ThreadPoolTaskExecutorHolder> ignored) {
        // ...见下方说明：executor 通过 ObjectProvider 延迟获取避免循环依赖
    }
}
```

> **实现说明（重要，按此写不要照抄上面占位）**：为避免 `CompileTaskService` 与自身 `@Async` 方法的自调用失效问题，实际结构应为——`submit()` 内通过注入的 `@Qualifier("compileTaskExecutor") ThreadPoolTaskExecutor` 的 `submit(Runnable)` 提交任务（**不用 `@Async`**），拒绝时捕获 `RejectedExecutionException` 将任务标记 failed 后重抛 `BusinessException(503, "编译任务队列已满，请稍后重试")`。字段：

```java
package com.pg.pgfplots.service;

import com.pg.pgfplots.common.BusinessException;
import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/** [G1] 编译异步任务服务（批次2）；[G2] Semaphore 限流 XeLaTeX 并发 */
@Slf4j
@Service
public class CompileTaskService {

    /** status: queued | running | success | failed（后两者终态） */
    public record CompileTask(String taskId, Integer userId, Integer historyId, String status,
                              String pdfPath, Long fileSize, String error, Long durationMs,
                              LocalDateTime createdAt) {}

    private static final int MAX_TASKS = 500;

    private final Map<String, CompileTask> tasks = new ConcurrentHashMap<>();
    private final Semaphore xelatexPermits;
    private final CompileService compileService;
    private final SystemLogWriter systemLogWriter;
    private final ThreadPoolTaskExecutor compileExecutor;

    public CompileTaskService(CompileService compileService, AppProperties appProperties,
                              SystemLogWriter systemLogWriter,
                              @Qualifier("compileTaskExecutor") ThreadPoolTaskExecutor compileExecutor) {
        this.compileService = compileService;
        this.systemLogWriter = systemLogWriter;
        this.compileExecutor = compileExecutor;
        this.xelatexPermits = new Semaphore(Math.max(1, appProperties.getLatex().getMaxConcurrency()));
    }

    /** 提交任务：归属与代码非空校验由 CompileService 的查询段在同步完成后进行（此处快速入队） */
    public CompileTask submit(Integer userId, Integer historyId) {
        if (tasks.size() >= MAX_TASKS) {
            evictOldestFinished();
        }
        String taskId = "ct_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 100000);
        CompileTask task = new CompileTask(taskId, userId, historyId, "queued",
                null, null, null, null, LocalDateTime.now());
        tasks.put(taskId, task);
        try {
            compileExecutor.submit(() -> run(taskId, userId, historyId));
        } catch (RejectedExecutionException e) {
            update(taskId, "failed", null, null, "编译任务队列已满", null);
            systemLogWriter.warning("[COMPILE] 任务队列已满，taskId=" + taskId + " 已标记失败");
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "编译任务队列已满，请稍后重试");
        }
        log.info("[COMPILE] 任务已提交 taskId={} historyId={} user={}", taskId, historyId, userId);
        return task;
    }

    /** 查询任务：归属校验防越权；不存在（含重启丢失）返回 null 由 Controller 转 404 */
    public CompileTask get(String taskId, Integer userId) {
        CompileTask task = tasks.get(taskId);
        return (task != null && task.userId().equals(userId)) ? task : null;
    }

    /** 工作线程主体：[G2] Semaphore 限流（队列已限流，阻塞等许可即可），耗时全程计时 */
    private void run(String taskId, Integer userId, Integer historyId) {
        long start = System.nanoTime();
        update(taskId, "running", null, null, null, null);
        try {
            xelatexPermits.acquire();
            try {
                Map<String, Object> result = compileService.compile(userId, String.valueOf(historyId));
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                Object size = result.get("file_size");
                update(taskId, "success", (String) result.get("pdf_path"),
                        size instanceof Number n ? n.longValue() : null, null, durationMs);
                log.info("[COMPILE] 任务成功 taskId={} durationMs={}", taskId, durationMs);
            } finally {
                xelatexPermits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(taskId, "failed", null, null, "编译任务被中断", null);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String msg = e.getMessage() == null ? "编译失败" : e.getMessage();
            update(taskId, "failed", null, null,
                    msg.length() > 500 ? msg.substring(0, 500) : msg, durationMs);
            log.warn("[COMPILE] 任务失败 taskId={} durationMs={}：{}", taskId, durationMs, msg);
            systemLogWriter.error("[COMPILE] 编译任务失败 taskId=" + taskId + "：" + msg);
        }
    }

    private synchronized void update(String taskId, String status, String pdfPath,
                                     Long fileSize, String error, Long durationMs) {
        CompileTask cur = tasks.get(taskId);
        if (cur == null) {
            return;
        }
        tasks.put(taskId, new CompileTask(cur.taskId(), cur.userId(), cur.historyId(), status,
                pdfPath != null ? pdfPath : cur.pdfPath(),
                fileSize != null ? fileSize : cur.fileSize(),
                error != null ? error : cur.error(),
                durationMs != null ? durationMs : cur.durationMs(),
                cur.createdAt()));
    }

    /** 内存表防膨胀：清除最旧的终态任务（success/failed） */
    private synchronized void evictOldestFinished() {
        tasks.values().stream()
                .filter(t -> "success".equals(t.status()) || "failed".equals(t.status()))
                .min(Comparator.comparing(CompileTask::createdAt))
                .ifPresent(t -> tasks.remove(t.taskId()));
    }
}
```

> 注意：`BusinessException(HttpStatus, String)` 构造已在现有代码中使用（`ChatService` L78 等同款）；`CompileService.compile` 失败会抛 `BusinessException`，在 `run` 的 catch 里统一转为任务 failed 状态，**不会**把异常抛给调用方。

### 5.3 T4：`controller/CompileController.java` [MODIFY]——两端点契约

**改造 1**：`POST /api/compile/{history_id}`（现 L31-34）——立即返回任务：

```java
/** [G1] 提交编译任务，立即返回 task_id（不再同步阻塞 30s） */
@PostMapping("/{history_id}")
public Result<Map<String, Object>> compile(@PathVariable("history_id") String historyId) {
    Integer userId = SecurityUtils.userId();
    CompileTaskService.CompileTask task = compileTaskService.submit(userId, parseHistoryId(historyId));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("task_id", task.taskId());
    data.put("status", task.status());
    data.put("history_id", task.historyId());
    return Result.ok("编译任务已提交", data);
}
```

**改造 2**：新增状态查询端点：

```java
/** [G1] 查询编译任务状态（终态 success 含 pdf_path/file_size/duration_ms；failed 含 error） */
@GetMapping("/task/{task_id}")
public Result<Map<String, Object>> task(@PathVariable("task_id") String taskId) {
    Integer userId = SecurityUtils.userId();
    CompileTaskService.CompileTask task = compileTaskService.get(taskId, userId);
    if (task == null) {
        throw new BusinessException(HttpStatus.NOT_FOUND,
                "任务不存在或已丢失（服务重启会清空任务状态，请重新提交编译）");
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("task_id", task.taskId());
    data.put("history_id", task.historyId());
    data.put("status", task.status());
    data.put("pdf_path", task.pdfPath());
    data.put("file_size", task.fileSize());
    data.put("error", task.error());
    data.put("duration_ms", task.durationMs());
    return Result.ok("查询成功", data);
}
```

> `parseHistoryId` 逻辑目前在 `CompileService` 私有段（L148-154）；把它提为 `CompileService` 的 public 静态工具或在 Controller 内复制同款（`Integer.parseInt`，非法抛 badRequest），二选一，优先提升为 `CompileService.parseHistoryIdPublic`。

**自检（T3/T4 完成后）**：`scripts\build.cmd && scripts\verify.cmd`——**此时 verify 第 5 段会失败（契约已变）**，属预期；直接跳到 T7 改完 verify 再回归，或临时用 curl 手工验证：

```cmd
:: 1) 提交（应返回 {"success":true,"data":{"task_id":"ct_...","status":"queued",...}}）
curl -X POST http://localhost:3000/api/compile/219 -H "Authorization: Bearer %TOKEN%"
:: 2) 轮询（status 应 queued→running→success，success 含 pdf_path/duration_ms）
curl http://localhost:3000/api/compile/task/ct_xxx -H "Authorization: Bearer %TOKEN%"
:: 3) 越权/乱 taskId 应 404 语义
```

### 5.4 T6：前端轮询改造

#### 5.4.1 `src/utils/compile.js` [NEW]（轮询工具，两视图复用）

```javascript
import axios from 'axios'
import { API_BASE_URL } from '../config'

/**
 * [G1] 提交编译任务并轮询至终态（1.5s 间隔，90s 上限 = 30s 编译超时 + 排队余量）
 * @returns {Promise<{pdf_path:string, file_size:number, duration_ms:number}>}
 * @throws {Error} message 为后端/超时的可展示文案
 */
export async function submitAndPollCompile(historyId, token) {
  const submit = await axios.post(`${API_BASE_URL}/api/compile/${historyId}`, {}, {
    headers: { Authorization: `Bearer ${token}` }, timeout: 15000
  })
  const taskId = submit.data && submit.data.data && submit.data.data.task_id
  if (!taskId) throw new Error('编译任务提交失败：未返回 task_id')

  const deadline = Date.now() + 90 * 1000
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 1500))
    const res = await axios.get(`${API_BASE_URL}/api/compile/task/${taskId}`, {
      headers: { Authorization: `Bearer ${token}` }, timeout: 15000
    })
    const task = res.data && res.data.data
    if (!task) throw new Error('编译任务查询失败')
    if (task.status === 'success') return task
    if (task.status === 'failed') throw new Error(task.error || '编译失败')
    // queued / running → 继续轮询
  }
  throw new Error('编译任务超时（90 秒），请稍后在历史记录中重试')
}
```

#### 5.4.2 `src/views/ChartGenerator.vue` [MODIFY]（`compileToPDF` L1001-1054）

- 保留 `compiling.value = true/false` loading 语义不变；
- 把 L1021-1025 的 `await axios.post(.../api/compile/${message.historyId}...)` 及后续 `fetchPdfBlobUrl` 替换为：

```javascript
import { submitAndPollCompile } from '@/utils/compile'
// ...
const task = await submitAndPollCompile(message.historyId, getAuthToken())
ElMessage.success(`PDF生成成功！`)
fetchPdfBlobUrl(message.historyId, token).then(showPdfPreview)
```

- 错误分支（L1033-1050）保持结构：把原来「LaTeX 日志截断 200 字符展示」的来源从同步响应改为 `err.message`（轮询工具已带后端 error 文案）。

#### 5.4.3 `src/views/MyHistory.vue` [MODIFY]（`compileToPDF` L479-528）

- 保留 `compilingId.value = historyId`（按行 loading）语义；
- 同款替换为 `await submitAndPollCompile(historyId, getAuthToken())` → `fetchPdfBlobUrl` 预览；
- 失败仍走既有 `compileErrorDialogVisible` Dialog，内容源改 `err.message`。
- `previewExistingPdf` / `fetchPdfBlobUrl`（utils/pdf.js）**不动**——PDF 下载接口契约未变。

**自检（T6 完成后）**：前端 `npm run serve` 起本地，在图表页/历史页各点一次编译：按钮 loading 直至 PDF 弹出；用 DevTools Network 确认请求序列 = 1 次 POST + N 次 GET task（N≥1）；失败路径可把 `app.latex.executable` 临时改成不存在的命令触发 failed（测完改回）。

### 5.5 T7：`spring-backend/scripts/verify.js` 第 5 段改造（L237-252）

原断言「POST compile → data.pdf_path」改为「POST → task_id → 轮询终态」；**保留语义**：编译失败仍走 `warnMsg`（不计 FAIL），无历史记录跳过。

```javascript
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
```

> 注意项数变化：原第 5 段 2 项（编译 + PDF），新版最多 3 项（终态 + duration + PDF）。基线总数可能从 27 变 28——**以「FAIL=0」为回归标准，PASS 总数允许 ±1**，并在批次2完成记录里注明新总数。

---

## 6. G2 并发控制（已并入 §5.2 的 T3，此处为行为定义）

- **限流点**：`CompileTaskService.run` 内 `xelatexPermits.acquire()` / `release()`（finally 保证释放），permits = `app.latex.max-concurrency`（默认 2）。
- **排队语义**：executor 队列（100）+ Semaphore（2）两级：任务先进池队列，运行前再拿许可；等待许可不设超时（池队列已是第一道背压，等待时长计入任务 duration_ms，可在监控中观察为「排队耗时」）。
- **可观测**：`GET /api/compile/task/{taskId}` 的 `duration_ms` 含排队时间；如需区分，可后续在 acquire 前后分段计时（本批不做）。
- **压测自检（T4 后可选）**：连续提交 4 个编译任务，观察日志时间戳——同一时刻最多 2 个任务处于 running（XeLaTeX 进程并发 ≤ 2）。

---

## 7. O1 链路耗时拆解（任务 T1 + T5）

### 7.1 ChatService 计时埋点 [MODIFY]

**成功路径**：`generateWithFallback` 调用处（现 L84-86）计时，写入 apiLog：

```java
// [O1] LLM 段耗时（含降级链重试的全部时间）
long llmStart = System.nanoTime();
FallbackResult result = generateWithFallback(systemPrompt, message, requestedModel);
long llmDurationMs = (System.nanoTime() - llmStart) / 1_000_000;
// ... recordFailedCall 所在 catch 各分支同样计时：catch 内 recordFailedCall(userId, e.getMessage(), llmDurationMs)
```

- `saveGenerationHistory` 增加参数 `Long durationMs`（或在 generate 内把 apiLog 构造参数化）——最小改法：`saveGenerationHistory(...)` 增加 `Long durationMs` 形参，`apiLog.setDurationMs(durationMs)`；
- `recordFailedCall(Integer userId, String errorMessage)` → 增加 `Long durationMs` 形参，失败也落耗时（失败耗时同样有分析价值）；三个 catch 分支各自传入当前计时时长；
- 空回复兜底抛出（L112-115）发生在计时段之后，耗时信息随 failed 分支落库即可，无需额外处理。

### 7.2 AdminLogService 真实聚合 [MODIFY]（替换硬编码假数据）

现状：`apiStats` L92-94 硬编码 `responseTime.put("avg", 245); responseTime.put("p95", 420);`。改为：

```java
// [O1] 从 selectApiLogs 已回传的明细行计算真实 avg/P95（无 duration 数据时输出 null，不再造假数据）
List<Map<String, Object>> durations = rows.stream()
        .map(r -> r.get("duration_ms"))
        .filter(v -> v instanceof Number)
        .map(v -> ((Number) v).longValue())
        .sorted()
        .toList();
if (!durations.isEmpty()) {
    double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
    long p95 = durations.get((int) Math.min(durations.size() - 1, Math.round(durations.size() * 0.95) - 1 < 0 ? 0 : Math.round(durations.size() * 0.95) - 1));
    responseTime.put("avg", Math.round(avg));
    responseTime.put("p95", p95);
    responseTime.put("sample_count", durations.size());
} else {
    responseTime.put("avg", null);
    responseTime.put("p95", null);
    responseTime.put("sample_count", 0);
}
```

> P95 用最朴素的「排序取第 95 百分位索引」实现（数据量为单日 api_log 明细，内存计算毫秒级）；`rows` 即 `adminMapper.selectApiLogs` 的返回，无需新增 SQL。

**同步改 `AdminMapper.xml` `selectApiLogs`（L113-125）**：SELECT 列表追加 `al.duration_ms,`（resultType=map 自动带出）。

**前端 AdminLog.vue**：responseTime 图表对 `null` 判空（无数据显示「暂无数据」而不是画 0）。改法：找到消费 `responseTime.avg/p95` 的 ECharts series 处，`v == null` 时不 push 数据点或置 display 空态。此改动极小，随 T5 一起做。

### 7.3 编译耗时

编译段耗时不落 `api_log`（语义属 LLM 调用表），记录在**任务终态 `duration_ms`**（§5.2 已实现），并已进 verify 断言。面试口径：LLM 耗时 → api_log（可按日聚合），编译耗时 → 任务表（内存）+ system_log 错误路径，两段分离、各得其所。

**自检（T5 完成后）**：
1. 触发一次成功生成（或复用 Trae 的 E1 脚本）→ `SELECT call_id, duration_ms FROM X.api_log ORDER BY call_id DESC LIMIT 1;` 有真实毫秒值；
2. `GET /api/admin/log/api-stats` 的 `responseTime` 不再是 245/420，且 `sample_count ≥ 1`；
3. 编译一次 → task 终态 `duration_ms` 为真实值（数千毫秒级）。

---

## 8. 总验收清单（全部通过才算批次2完成）

| # | 验收项 | 方法 | 通过标准 |
|---|---|---|---|
| 1 | 基线不回归 | `scripts\verify.cmd` | FAIL=0（PASS 总数允许 27→28，第 5 段新增 duration 断言） |
| 2 | 提交即返回 | POST /api/compile/{hid} | 响应 < 200ms，返回 `{task_id, status:"queued"}`，不再阻塞 30s |
| 3 | 轮询终态 | GET /api/compile/task/{taskId} | queued→running→success（success 含 pdf_path/file_size/duration_ms） |
| 4 | 越权防护 | 用用户 B 的 token 查用户 A 的 taskId | 404 语义，不泄露任务存在性 |
| 5 | 重启语义 | 重启后端后查旧 taskId | 404 语义 + 明确提示文案 |
| 6 | 并发限流 | 连续提交 ≥4 个编译任务 | 同一时刻 running 的 XeLaTeX ≤ `max-concurrency`（日志时间戳验证） |
| 7 | 队列满语义 | 临时把 queueCapacity 调 1 并占满 | 提交返回 503「编译任务队列已满」，任务状态 failed（不静默丢弃） |
| 8 | 前端联动 | 图表页 + 历史页各编译一次 | loading 期间轮询可见；PDF 正常弹出；失败弹窗带后端 error 文案 |
| 9 | O1 落库 | 成功/失败生成各一次 | api_log.duration_ms 均为真实毫秒值 |
| 10 | O1 聚合 | GET /api/admin/log/api-stats | responseTime 为真实值 + sample_count；AdminLog.vue 无数据时判空不画 0 |
| 11 | P95 计算 | 构造 ≥20 条不同 duration 的 api_log | p95 接近第 95 百分位（抽样手算对比） |

---

## 9. 回归说明（执行节奏）

1. 每任务（T1–T7）完成：`mvn -q -DskipTests compile`。
2. **T4 完成后 verify 第 5 段必然失败（契约变更），这是唯一允许的中间态**——立即做 T7，两者同批回归。
3. 大节回归点：T4+T7 后（G1 契约闭环）、T8 全部完成后。
4. 全部完成：回填 `plan-AI.md` §4 批次 2 状态 + `docs/log.md` 第八章追加「批次2」小节（格式沿用批次1 记录）。

---

## 附录：Trae 分工建议（本批外可并行）

| 任务 | 给 Trae 的规格来源 | 约束 |
|---|---|---|
| T12/E1（已分配） | 本仓 `plan-AI-批次1-执行方案.md` §8 | 只新建 `spring-backend/eval/`，禁改 Java |
| G6 路由守卫 | `src/router/index.js`（109 行） | 参照既有管理员守卫 L100-106 的写法；14 条路由全量覆盖；禁改其他文件 |
| G5 request 层 | 需先出规格（拦截器 + 迁移清单），建议下一批 | 迁移期保留各组件现有调用兼容，禁止一刀切 |
| 文档同步 | README/development.md 补批次1 变更（RAG 配置、rag_*.cmd、.env 新变量） | 纯文档 |
| 种子模板初稿 | SYSTEM_PROMPT R1-R8 规则 | 只产出文本供审核，不直接入库 |

---

## 附：与 plan-AI.md 的映射

| 本文件 | plan-AI.md |
|---|---|
| §5 G1 | §7「G1 轻量版」契约（`{task_id, status:"queued"}` + `GET /api/compile/task/{taskId}`） |
| §3.1/§6 G2 | §7「G2 并发控制」（max-concurrency + Semaphore） |
| §2/§7 O1 | §7「O1 耗时拆解」（duration_ms 列 + AdminLogService 聚合） |
| §8 | §11 验收（编译 P95 < 100ms 的提交响应语义） |








  
