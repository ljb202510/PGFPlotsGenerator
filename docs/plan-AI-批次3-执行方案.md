# 批次 3 可执行执行方案（E2 失败归类 + E3 质量看板 + 评估集 V2）

> **本文档是自包含的施工图**：执行者拿到本文档即可直接动工，无需回读 `plan-AI.md` 或批次2 施工图。
> 项目：PGFPlotsGenerator 后端（`hello/spring-backend/`）+ 前端（`hello/src/`，Vue 3.2 + element-plus + echarts）+ 离线评估（`hello/spring-backend/eval/`）
> 前置：批次0（技术栈迁移）、批次1（A1 RAG / A2 / A3 / E1）、批次2（G1 编译队列化 / G2 并发控制 / O1 耗时拆解）均已完成并验收，基线 **PASS=29 / FAIL=0 / WARN=0**。日期：2026-09-14
> **范围**：仅 E2 / E3 / 评估集 V2 / 回归断言。
> **明确不做**：按日成功率折线（趋势图叠加双 y 轴）、失败明细筛选下钻表、独立失败事件表（`failure_event`）、语义级规则 R4（量纲一致性）的静态检测、批次4/5 各项（A4 / G3 / G4 / O2 / O4 / A5 / A6 / O3 / G5 / G6）。
> **交付性质**：复用既有表与既有监控页的增量改造，**不新增接口端点、不新增中间件、不新增依赖**。
> **行号说明**：本文档所引行号基于 2026-09-14 的代码快照；若与实际漂移，**以符号名/方法名为准**，行号仅作定位参考。

---

## 0. 硬性约束（先读，违反即返工）

1. **`CODE_EXTRACT_FAIL` 必须单行落库**：归为 `failed` 的同时**不得**先写一条 success 再补一条 failed。做法是把分类作为参数传入 `ChatService.saveGenerationHistory(...)`，由「分类是否为空」一并决定 `call_status`，否则会**双计 `summary.total_calls`**。
2. **编译类失败只写一处**：`COMPILE_ERROR` / `COMPILE_QUEUE_FULL` 只在 `CompileTaskService`（编译任务的终态判定处）落库。`CompileTaskService.run` 是全仓 `compileService.compile(...)` 的**唯一调用点**（已 grep 确认），**禁止**再在 `CompileService` 内部补写，否则一次失败计两条。
3. **空态 / null 判空三条规则必须实现**（详见 §6.3），且必须作为验收项逐条核对：
   - ① `sample_count === 0` → 不 `setOption`，显示空态文案；
   - ② `avg` / `p95` 为 `null` → 显示「—」，**不得**被 `|| 0` 兜底成 0；
   - ③ `errorTypes` 为空数组 → 不画「全 0 饼图」，显示空态文案。
4. **不新增端点、不新增聚合 SQL**：E2/E3 的新数据一律挂在既有 `GET /api/admin/log/api-stats` 响应上，聚合复用 `AdminMapper.selectApiLogs` 已取回的明细行（内存计算，毫秒级）。
5. **`api_log` 语义扩展必须显式声明**：它从「LLM 调用日志」扩展为「**AI 链路失败事件日志**」（新增编译类分类）。实体注释、`AdminLogService` 注释与本文档口径必须一致，避免后来者误判「api_log 只有 LLM 调用」。
6. **编译逻辑不动**：`LatexCompiler`、`CompileService` 的校验/预处理/超时/产物移动逻辑一律不改；本批只在 `CompileTaskService` 的失败分支补一次落库。
7. **回归节奏**：每任务 `mvn -q -DskipTests compile`；每大节 `scripts\build.cmd && scripts\verify.cmd`。回归标准是 **FAIL=0**，通过数预期由 29 升至 31（见 §8），属预期变化。
8. **风格**：`@RequiredArgsConstructor`、`[TAG]` 中文日志、`Result` 统一响应、迁移 SQL 放 `hello/migrations/`、配置项 `${ENV:default}`、前端 import 用 `@/` 别名（如 `import { API_BASE_URL } from '@/config';`）。

---

## 1. 前置检查（T0）

| # | 检查项 | 通过标准 |
|---|---|---|
| 1 | `scripts\build.cmd && scripts\verify.cmd` | PASS=29 FAIL=0 WARN=0 |
| 2 | MySQL 可达（root/000） | 连接成功 |
| 3 | `SHOW COLUMNS FROM X.api_log LIKE 'error_type';` | 空（尚未迁移） |
| 4 | `xelatex --version` | 有版本号（编译失败造数依赖它存在） |
| 5 | `cd hello && npm run serve` | 前端可编译起服务（E3 需目视验收） |

**环境事实（本批依赖）**
- `api_log` 现有列：`call_id` / `user_id` / `history_id` / `call_status` / `call_time` / `call_error` / `prompt_version`（批次1 迁移）/ `duration_ms`（批次2 迁移）。
- 迁移执行方式沿用仓库习惯：`mysql -u root -p000 X < migrations/xxx.sql`（在 `hello/` 目录下执行），随后用 `SHOW COLUMNS` 自检。
- 编译产物目录：`hello/data/storage/generated_charts/user{userId}/`；失败样本目录 `hello/data/storage/debug/`。
- 后端启动需在 `spring-backend` 目录（`spring.config.import: optional:file:../data/.env[.properties]`）。

---

## 2. 数据库迁移与实体（T1）

新建 `hello/migrations/alter_api_log_error_type.sql`：

```sql
-- 批次3/E2：api_log 记录失败分类，供管理端聚合「失败归类」计数
USE `X`;

ALTER TABLE api_log
  ADD COLUMN error_type VARCHAR(32) NULL AFTER duration_ms;
```

同步 **`entity/ApiLog.java`** [MODIFY]——在 `durationMs` 之后追加：

```java
/** 失败分类（批次3/E2），取值见 {@link com.pg.pgfplots.common.ErrorTypes}；成功调用为 null */
private String errorType;
```

**自检**：`SHOW COLUMNS FROM X.api_log LIKE 'error_type';` 有列；`mvn -q -DskipTests compile` 通过。

---

## 3. E2：失败分类枚举与埋点（T2）

### 3.0 分类与枚举（新增 `common/ErrorTypes.java` [NEW]）

分类字符串分散在 `ChatService` / `CompileTaskService` / `AdminLogService` 三处使用，为避免拼写漂移，新增一个小常量类（沿用 `common/` 包惯例，`final` + 私有构造）：

```java
package com.pg.pgfplots.common;

/** 失败分类枚举值（批次3/E2）：写入 api_log.error_type，由管理端聚合「失败归类」计数 */
public final class ErrorTypes {

    private ErrorTypes() {
    }

    /** 上游 API 返回错误（LlmApiException） */
    public static final String UPSTREAM_API_ERROR = "UPSTREAM_API_ERROR";
    /** 无法连接上游（LlmConnectionException） */
    public static final String UPSTREAM_CONNECTION_ERROR = "UPSTREAM_CONNECTION_ERROR";
    /** 上游其他异常 */
    public static final String UPSTREAM_UNKNOWN_ERROR = "UPSTREAM_UNKNOWN_ERROR";
    /** 上游返回空内容（空回复兜底） */
    public static final String EMPTY_REPLY = "EMPTY_REPLY";
    /** 生成成功但未提取到可编译的图表代码 */
    public static final String CODE_EXTRACT_FAIL = "CODE_EXTRACT_FAIL";
    /** XeLaTeX 编译失败 */
    public static final String COMPILE_ERROR = "COMPILE_ERROR";
    /** 编译任务被线程池拒绝（队列已满） */
    public static final String COMPILE_QUEUE_FULL = "COMPILE_QUEUE_FULL";
}
```

| 分类值 | 触发点（落点） | call_status |
|---|---|---|
| `UPSTREAM_API_ERROR` | `ChatService` 的 `catch (LlmApiException e)` | failed |
| `UPSTREAM_CONNECTION_ERROR` | `ChatService` 的 `catch (LlmConnectionException e)` | failed |
| `UPSTREAM_UNKNOWN_ERROR` | `ChatService` 的 `catch (Exception e)` | failed |
| `EMPTY_REPLY` | `ChatService` 空回复兜底（**现状完全未落库，属补齐项**） | failed |
| `CODE_EXTRACT_FAIL` | `ChatService` 解析后 `finalChartCode` 为空（**新增判定**） | failed |
| `COMPILE_ERROR` | `CompileTaskService.run` 的失败分支 | failed |
| `COMPILE_QUEUE_FULL` | `CompileTaskService.submit` 的入队被拒分支 | failed |

### 3.1 为什么 `CODE_EXTRACT_FAIL` 记为 `failed`（口径说明，含一行翻转）

- `plan-AI.md` §8 把「围栏不闭合」列为**失败**类别，本批与之对齐；
- 该产出对用户**无效**：历史记录里 `generation_code` 为空，用户点「生成PDF」必然得到 400「该历史记录没有可编译的图表代码」，只能重试；
- 记为 failed 后，E3 看板的成功率曲线才**真实反映质量**，且与离线评估 `eval.mjs` 的 `first_pass_rate` 口径（`code_ok` 非空才算通过）一致。
- **翻转方式（若将来要改口径）**：把 `ChatService` 中 `errorType` 的赋值改为不影响 `call_status`（即 `saveGenerationHistory` 内固定写 `success`，只写 `error_type`），一行即可——届时「失败归类」计数仍按 `error_type` 聚，但不再计入失败数。

### 3.2 `ChatService.java` [MODIFY]

> 需追加 `import com.pg.pgfplots.common.ErrorTypes;`（其余 import 不动）。

**（a）三个 catch 分支补分类**（现状已是 `recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart))`）：

```java
// catch (LlmApiException e)
recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_API_ERROR);
// catch (LlmConnectionException e)
recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_CONNECTION_ERROR);
// catch (Exception e)
recordFailedCall(userId, e.getMessage(), elapsedMs(llmStart), ErrorTypes.UPSTREAM_UNKNOWN_ERROR);
```

**（b）空回复兜底补齐落库**（现状只写 `system_log` 后抛 502，属缺口）：

```java
// 空回复兜底
if (aiReply == null || aiReply.trim().isEmpty()) {
    systemLogWriter.error("[CHAT] " + label + " 内容为空 + 降级全失败 (用户 " + userId + ")");
    // [E2] 补齐：空回复此前只写 system_log，未落 api_log，导致失败归类漏掉一整类
    recordFailedCall(userId, "AI 返回内容为空", elapsedMs(llmStart), ErrorTypes.EMPTY_REPLY);
    throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 返回内容为空，请重试。");
}
```

**（c）代码提取失败判定 + 单行落库**（解析段之后、保存历史之前）：

```java
// [E2] 生成成功但拿不到可编译代码 → 归为 CODE_EXTRACT_FAIL（单行落库，禁止双计）
String errorType = (finalChartCode == null || finalChartCode.trim().isEmpty())
        ? ErrorTypes.CODE_EXTRACT_FAIL : null;

// 保存历史（[O1] 带耗时；[E2] 带失败分类）
SavedHistory saved = saveGenerationHistory(userId, message, aiReply, request.getDataIds(),
        finalChartCode, llmDurationMs, errorType);
```

**（d）`saveGenerationHistory` 增参并由分类决定状态**：

```java
private SavedHistory saveGenerationHistory(Integer userId, String userInput, String aiResponse,
                                           List<Integer> dataIds, String chartCode, Long durationMs,
                                           String errorType) {
    // ...
    ApiLog apiLog = new ApiLog();
    apiLog.setUserId(userId);
    apiLog.setHistoryId(historyId);
    // [E2] 分类非空即失败：同一条记录内决定状态，避免先写 success 再补 failed 造成双计
    apiLog.setCallStatus(errorType == null ? "success" : "failed");
    apiLog.setCallTime(LocalDateTime.now());
    apiLog.setPromptVersion(PromptTemplates.VERSION);
    apiLog.setDurationMs(durationMs);
    if (errorType != null) {
        apiLog.setErrorType(errorType);
        // 让「最近失败调用」表的描述列有可读文案，而不是空白
        apiLog.setCallError("未从生成内容中提取到可编译的图表代码");
    }
    apiLogMapper.insert(apiLog);
    // ... REST 不变（JSON 落盘、RAG 异步索引、返回 SavedHistory）
}
```

> 注意：**不要**因为 `finalChartCode` 为空就跳过 `saveGenerationHistory`——历史记录（含 AI 原文）仍然要落库，用户可以在历史页重试生成；本批只改「这条 api_log 记成什么状态」。

**（e）`recordFailedCall` 增参**：

```java
private void recordFailedCall(Integer userId, String errorMessage, Long durationMs, String errorType) {
    // ... 原逻辑不变，构造 ApiLog 后追加：
    apiLog.setErrorType(errorType);
}
```

**自检（T2 完成后）**：`mvn -q -DskipTests compile`；`grep` 确认 `recordFailedCall` 的 4 个调用点（3 个 catch + 空回复）与 `saveGenerationHistory` 的 1 个调用点参数个数一致，无遗留旧签名。

### 3.3 `CompileTaskService.java` [MODIFY]——编译类失败落库

**（a）注入 `ApiLogMapper`**：该类使用手写构造器（需初始化 `Semaphore`），在参数表末尾追加 `ApiLogMapper apiLogMapper` 并赋值，同时新增 `import com.pg.pgfplots.entity.ApiLog;` / `mapper.ApiLogMapper` / `common.ErrorTypes` / `util.PromptTemplates`（**不**引入 PromptTemplates，见下）。

**（b）新增私有落库方法（副作用式，自身失败不影响任务终态流转）**：

```java
/**
 * [E2] 编译类失败事件落库（api_log 语义扩展为「AI 链路失败事件日志」）。
 * <p>不写 prompt_version：编译发生在生成之后，此处无法得知当次生成所用版本，写当前版本会造成误读，留 NULL。</p>
 */
private void recordCompileFailure(Integer userId, Integer historyId, String errorType,
                                  String message, Long durationMs) {
    try {
        String msg = message == null ? "编译失败" : message;
        ApiLog apiLog = new ApiLog();
        apiLog.setUserId(userId);
        apiLog.setHistoryId(historyId);
        apiLog.setCallStatus("failed");
        apiLog.setCallTime(LocalDateTime.now());
        apiLog.setErrorType(errorType);
        apiLog.setDurationMs(durationMs);
        apiLog.setCallError(msg.length() > 500 ? msg.substring(0, 500) : msg);
        apiLogMapper.insert(apiLog);
    } catch (Exception e) {
        log.error("[COMPILE] 失败事件落库出错: {}", e.getMessage());
    }
}
```

**（c）三处调用**（**只在这一个类里写**，禁止在 `CompileService` 内补写）：

| 位置 | 调用 | durationMs |
|---|---|---|
| `submit` 的 `catch (RejectedExecutionException e)` | `recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_QUEUE_FULL, "编译任务队列已满", null)` | null |
| `run` 的 `catch (InterruptedException e)` | `recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_ERROR, "编译任务被中断", null)` | null |
| `run` 的 `catch (Exception e)` | `recordCompileFailure(userId, historyId, ErrorTypes.COMPILE_ERROR, msg, durationMs)` | 已计时的 `durationMs` |

> 顺序要求：在 `update(taskId, "failed", ...)` 与 `systemLogWriter` 之后调用即可；`recordCompileFailure` 自身吞异常，不得影响既有的任务状态流转与 503 抛出。

**自检（T3 完成后）**：`mvn -q -DskipTests compile`；造一次编译失败（见 §10#5）后查 `SELECT call_id, call_status, error_type, history_id, call_error FROM X.api_log ORDER BY call_id DESC LIMIT 1;`。

---

## 4. E2：失败分类聚合（T3）

### 4.1 `resources/mapper/AdminMapper.xml` [MODIFY]

`selectApiLogs` 的 SELECT 列表追加 `al.error_type,`（紧跟 `al.duration_ms,`；`resultType=map` 自动带出，`WHERE`/`ORDER BY` 不动）。

### 4.2 `service/AdminLogService.java` [MODIFY]

在 `apiStats(...)` 的 `responseTime` 计算段之后追加下面的**计算块**；`data.put("errorTypes", errorTypes);` 要放在 `Map<String, Object> data = new LinkedHashMap<>();` 声明之后（与既有的 `data.put("responseTime", responseTime)` 并列，**顺序无关但位置必须在 `data` 声明之后**）：

```java
// [E2] 失败分类计数（按 error_type 聚合；与 call_status 无关，成功行不带分类）
Map<String, Integer> typeCounts = new LinkedHashMap<>();
for (Map<String, Object> log : logs) {
    Object type = log.get("error_type");
    if (type == null) {
        continue;
    }
    typeCounts.merge(type.toString(), 1, Integer::sum);
}
List<Map<String, Object>> errorTypes = typeCounts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("error_type", entry.getKey());
            item.put("count", entry.getValue());
            return item;
        })
        .toList();
data.put("errorTypes", errorTypes);
```

**响应形状**（不新增端点，挂在既有 `GET /api/admin/log/api-stats`）：

```json
{
  "summary":  { "total_calls": 42, "success_calls": 39, "failed_calls": 3, "success_rate": 92.86 },
  "timeSeries": [ { "date": "2026-09-14", "total": 42, "success": 39, "failed": 3 } ],
  "recentFailures": [ { "...": "..." } ],
  "responseTime": { "avg": 3157, "p95": 3157, "sample_count": 1 },
  "errorTypes": [ { "error_type": "COMPILE_ERROR", "count": 2 },
                  { "error_type": "EMPTY_REPLY", "count": 1 } ]
}
```

- 无任何失败分类时 `errorTypes` 为 `[]`（**前端必须按空数组走空态**）。
- 聚合口径：**按 `error_type` 计**，不去重、不分页；`recentFailures` 字段本次**不动**（本批不做失败明细表改造）。

**自检**：`mvn -q -DskipTests compile`；`scripts\build.cmd && scripts\verify.cmd` → FAIL=0（此时 §8 的新断言尚未加，通过数仍为 29）。

---

## 5. E3：耗时分位后端聚合（T4）

### 5.1 `service/AdminLogService.java` [MODIFY]

**（a）抽出百分位辅助方法**（`responseTime` 与新增的按日序列共用同一公式，避免两处各写一遍）：

```java
/** 最近秩法（nearest-rank）：升序列表中第 ceil(n*0.95) 个样本 */
private long p95Of(List<Long> sortedAsc) {
    int index = (int) Math.ceil(sortedAsc.size() * 0.95) - 1;
    return sortedAsc.get(Math.max(0, Math.min(sortedAsc.size() - 1, index)));
}
```

并把 `apiStats` 中**已有的** `responseTime` 计算段（批次2/O1 实现，即对 `durations` 排序后取 `ceil(n*0.95)` 的那几行）改为调用 `p95Of(durations)`（数值结果必须与改造前完全一致，属等价重构）。

**（b）新增按日耗时序列**（数据源仍是已取回的 `selectApiLogs` 明细，**不新增 SQL**；同样地，`data.put("responseTimeSeries", …)` 必须放在 `data` 声明之后）：

```java
// [E3] 按日耗时序列（avg / P95 / 样本数）：无 duration_ms 的日期不产生数据点，区间内全无则为空数组
Map<String, List<Long>> durationsByDate = new TreeMap<>();
for (Map<String, Object> log : logs) {
    Object duration = log.get("duration_ms");
    if (!(duration instanceof Number)) {
        continue;
    }
    String date = extractDate(log.get("call_time"));
    if (date.isEmpty()) {
        continue;
    }
    durationsByDate.computeIfAbsent(date, key -> new ArrayList<>()).add(((Number) duration).longValue());
}
List<Map<String, Object>> responseTimeSeries = new ArrayList<>(durationsByDate.size());
for (Map.Entry<String, List<Long>> entry : durationsByDate.entrySet()) {
    List<Long> values = entry.getValue();
    values.sort(Long::compareTo);
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("date", entry.getKey());
    item.put("avg", Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0)));
    item.put("p95", p95Of(values));
    item.put("count", values.size());
    responseTimeSeries.add(item);
}
data.put("responseTimeSeries", responseTimeSeries);
```

- 复用既有私有方法 `extractDate(Object callTime)`（`systemLogs` 之外、`apiStats` 里算 `timeSeries` 时已在用），无需新写日期解析。
- 复杂度：按日期分组 + 每组排序，`O(n log n)`，数据量为单次查询的 api_log 明细（单日量级），毫秒级。
- **退化备选**（默认不采用）：若不想加按日序列，可退化为「avg vs P95 双柱」静态图，`responseTime` 已够用；此时本节的 `responseTimeSeries` 与 §6.2 的「耗时趋势」图一并省略。

**自检**：`mvn -q -DskipTests compile`；起服务后 `GET /api/admin/log/api-stats`，确认 `responseTimeSeries` 是数组（有耗时数据时元素含 `date/avg/p95/count`）。

---

## 6. E3：前端质量看板（T5）

### 6.1 模板改动（`src/views/AdminLog.vue` [MODIFY]）

**（a）健康概览卡片区追加 2 张耗时卡片**（`div.overview-cards`，现为 4 张：系统状态 / API成功率 / 最近错误 / 活跃用户）：

```html
<el-card class="overview-card">
  <div class="card-content">
    <div class="card-icon primary"><el-icon><Timer /></el-icon></div>
    <div class="card-info">
      <div class="card-title">平均耗时</div>
      <div class="card-value">{{ apiStats.responseTime.avg == null ? '—' : apiStats.responseTime.avg + ' ms' }}</div>
      <div class="card-subtitle">样本 {{ apiStats.responseTime.sample_count || 0 }}</div>
    </div>
  </div>
</el-card>
<el-card class="overview-card">
  <div class="card-content">
    <div class="card-icon warning"><el-icon><Timer /></el-icon></div>
    <div class="card-info">
      <div class="card-title">P95 耗时</div>
      <div class="card-value">{{ apiStats.responseTime.p95 == null ? '—' : apiStats.responseTime.p95 + ' ms' }}</div>
      <div class="card-subtitle">样本 {{ apiStats.responseTime.sample_count || 0 }}</div>
    </div>
  </div>
</el-card>
```

- 需在 `@element-plus/icons-vue` 的解构 import 中追加 `Timer`（该图标库已含，无需新依赖）。
- 新增 `.card-subtitle` 样式（`.card-info` 下小号灰字，`font-size: 12px; color: #909399;`）。

**（b）新增「质量看板」区块**，插在现有 `div.chart-section`（API调用统计，L69-139）**之后**、`div.log-section`（L142 起）**之前**：

```html
<div class="chart-section">
  <h2>质量看板</h2>
  <div class="quality-grid">
    <div class="chart-wrapper">
      <div v-if="errorChartEmpty" class="chart-empty">暂无失败记录</div>
      <div v-else ref="errorChartRef" style="width: 100%; height: 360px;"></div>
    </div>
    <div class="chart-wrapper">
      <div v-if="latencyChartEmpty" class="chart-empty">该区间暂无耗时数据</div>
      <div v-else ref="latencyChartRef" style="width: 100%; height: 360px;"></div>
    </div>
  </div>
</div>
```

- `.quality-grid`（新增样式）：`display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 20px;`——与既有 `.overview-cards`（L728-732）同款写法，窄屏自动降为单列。
- `.chart-empty`（新增样式）：居中灰字空态，`height: 360px; display: flex; align-items: center; justify-content: center; color: #909399;`。
- **注意**：现有 `.chart-container` 在模板里存在（L71）但 CSS 里**没有**对应选择器（已核实），新代码不要依赖它。

### 6.2 脚本改动

**（a）新增状态**（在 `const chartRef = ref(null)` / `let chartInstance = null`（L282-283）附近）：

```js
const errorChartRef = ref(null)
const latencyChartRef = ref(null)
let errorChartInstance = null
let latencyChartInstance = null
const errorChartEmpty = ref(true)
const latencyChartEmpty = ref(true)

// [E2] 失败分类 → 中文标签；未收录的新分类直接显示原始值（不透掉）
const ERROR_TYPE_LABELS = {
  UPSTREAM_API_ERROR: '上游接口错误',
  UPSTREAM_CONNECTION_ERROR: '上游连接失败',
  UPSTREAM_UNKNOWN_ERROR: '上游未知错误',
  EMPTY_REPLY: '空回复',
  CODE_EXTRACT_FAIL: '代码提取失败',
  COMPILE_ERROR: '编译失败',
  COMPILE_QUEUE_FULL: '编译队列已满'
}
const errorTypeLabel = (type) => ERROR_TYPE_LABELS[type] || type
```

**（b）`apiStats` 响应式对象补齐字段**（现为 `{ summary, timeSeries, recentFailures, responseTime }`，L294-299）：

```js
const apiStats = reactive({
  summary: {},
  timeSeries: [],
  recentFailures: [],
  responseTime: {},
  errorTypes: [],
  responseTimeSeries: []
})
```

**（c）实例初始化**：沿用现有 `initChart()`（L394-406）的写法，为两个新容器各 `echarts.init` + 各自 `ResizeObserver`（**只在容器存在时 init**——空态时 `ref` 为 `v-if=false` 不渲染，所以渲染函数需在切换 `v-if` 后 `nextTick` 再 init）。

> 实现提示（避免踩坑）：不要照抄「onMounted 里一次性 init 三个」，而应写成 `ensureInstance(refValue, currentInstance)` 之类的小工具，在渲染前判断实例是否存在、容器是否存在，不存在则创建。原因：两个新容器被 `v-if` 控制，首屏可能不存在。

**（d）两个渲染函数**：

```js
// 失败分类分布（饼图）
const renderErrorChart = async () => {
  const list = apiStats.errorTypes || []
  errorChartEmpty.value = list.length === 0
  if (errorChartEmpty.value) {
    if (errorChartInstance) { errorChartInstance.clear() }
    return
  }
  await nextTick()
  errorChartInstance = ensureInstance(errorChartRef.value, errorChartInstance)
  if (!errorChartInstance) return
  errorChartInstance.setOption({
    title: { text: '失败分类分布', left: 'center' },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      data: list.map((it) => ({ name: errorTypeLabel(it.error_type), value: it.count }))
    }]
  }, true)
}

// 耗时趋势（按日 avg / P95 双折线）
const renderLatencyChart = async () => {
  const list = apiStats.responseTimeSeries || []
  latencyChartEmpty.value = list.length === 0
  if (latencyChartEmpty.value) {
    if (latencyChartInstance) { latencyChartInstance.clear() }
    return
  }
  await nextTick()
  latencyChartInstance = ensureInstance(latencyChartRef.value, latencyChartInstance)
  if (!latencyChartInstance) return
  latencyChartInstance.setOption({
    title: { text: '耗时趋势（按日）', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均', 'P95'], top: 24 },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '20%', containLabel: true },
    xAxis: { type: 'category', data: list.map((it) => it.date) },
    yAxis: { type: 'value', name: '毫秒' },
    series: [
      { name: '平均', type: 'line', smooth: true, data: list.map((it) => it.avg) },
      { name: 'P95', type: 'line', smooth: true, data: list.map((it) => it.p95) }
    ]
  }, true)
}
```

- `setOption(option, true)` 用 `true` 做 notMerge，避免筛选日期后切换数据时残留上一次的系列。
- `fetchApiStats()`（L431-461）中 `Object.assign(apiStats, result.data)` 之后，把现有的 `renderChart()` 调用扩为：

```js
Object.assign(apiStats, result.data)
renderChart()
renderErrorChart()
renderLatencyChart()
```

- 需在 import 中追加 `nextTick`（`import { ref, reactive, onMounted, computed, nextTick } from 'vue'`，L266）。`ensureInstance` 小工具建议直接写在本文件（不要新建 util 文件，保持单文件改动）。

### 6.3 空态 / null 判空三条规则（**验收项，逐条核对**）

| # | 规则 | 落点 | 核对方法 |
|---|---|---|---|
| 1 | `sample_count === 0` → 不画图、显示空态 | 两张耗时卡片显示「—」+ 样本 0；两张图表由 `errorChartEmpty` / `latencyChartEmpty` 控制 | 选一个无数据的日期区间（如批次2 之前），确认无图、无 0 值柱、无空饼图 |
| 2 | `avg` / `p95` 为 `null` → 显示「—」 | 卡片模板用 `== null ? '—' : …`，**禁止** `|| 0` | 同上，确认卡片是「—」而不是「0 ms」 |
| 3 | `errorTypes` 为空数组 → 空态，不画全 0 饼图 | `renderErrorChart` 首行的空数组判断 | 无失败数据时确认显示「暂无失败记录」且未 `setOption` |

**自检（T4+T5 完成后）**：
1. `cd hello && npm run serve`，用 `admin123/666666` 登录管理端进入 `/admin` 监控页；
2. DevTools Network 确认 `/api/admin/log/api-stats` 的响应含 `errorTypes` 与 `responseTimeSeries`；
3. 目视：质量看板两张图正常渲染；切换日期区间到「无数据区间」时两张图与两张卡片全部进入空态，**没有** 0 值或空饼图；
4. 造一次编译失败（§10#5）后刷新，确认「失败分类分布」出现「编译失败」扇区。

---

## 7. 评估集 V2：静态违例检测与用例扩充（T6）

> 目标：把「通过」从「chat 成功 + 代码非空 + 编译成功」细化为「**通过且零违例**」，让 RAG / 提示词迭代的增量可被量化。
> 全部改动在 `hello/spring-backend/eval/` 内，**零第三方依赖**，不触碰任何 Java 代码。

### 7.1 新增 `eval/violations.mjs` [NEW]

```js
/**
 * 静态违例检测（批次3/评估集 V2，零依赖）。
 * 按 util/PromptTemplates.java 的【图表渲染规则】R1-R8 做正则启发式检查。
 * 注意：这是启发式检测，不追求零误报；命中即提示人工复核。
 * @param {string} chartCode 模型生成的 LaTeX 图表代码
 * @returns {Array<{rule: string, reason: string}>} 违例列表（无违例为空数组）
 */
export function detectViolations(chartCode) { /* 见下表规则 */ }
```

**七类可静态检测的规则（与 `PromptTemplates` R1-R8 逐条对应）**

| 规则 | 检测方式（启发式） | 误报风险 |
|---|---|---|
| `R1_LEGEND_POS` | 命中 `legend pos=north west` / `north east`，或 `legend to name=` → 违例 | 低 |
| `R2_NODE_OUTSIDE` | 命中 `\node at (current bounding box` → 违例 | 低 |
| `R3_NODES_PAIRED` | ①有 `mark=` 但无 `nodes near coords`；②出现 `nodes near coords style=`；③有 `nodes near coords` 但缺少 `fill=none` 或 `draw=none` → 违例 | 中（③为粗粒度，只要全文出现过即可） |
| `R5_ERROR_BARS` | ①无 `+-` 却出现 `error bars`；②`coordinates { … }` 内出现三元组 `(x,y,err)` → 违例 | 低 |
| `R6_SINGLE_TIKZ` | `\begin{tikzpicture}` 出现次数 ≠ 1；或命中 `\caption` / `\label` / `\ref` / `figure` → 违例 | 低 |
| `R7_FULLWIDTH_COMMA` | ①`symbolic x coords={…}` 捕获组内含全角 `，`；②全文含全角 `，`（`ChatService` 编译前会归一，但生成源码含全角即属未遵守规则）→ 违例 | 低 |
| `R8_LABEL_STAGGER` | 统计 `nodes near coords` 出现次数 ≥2，但 `anchor=south` 与 `anchor=north` 只出现其一 → 违例 | 中（仅在多系列场景生效） |

**明确不做（诚实口径，必须写进代码注释与本节）**：

- **`R4` 单位/量级一致性不做静态检测**。它要求判断「ylabel 的单位（如（万人））」与「coordinates 里的数值量级」是否自洽（例如原始值 ≥100000 应先除以 10000 再入图），这依赖**语义理解**（要知道这份数据是"万元"还是"元"、是"人"还是"万人"）。用正则会大量误报（把合法的 `ylabel={人口}` + `coordinates {(2019,1000)}` 判为违规），**做一个不可信的检查比不做更糟**——评估数字会被人为污染。留作人工评审项。
- 同理不做：图例是否真的遮挡数据（需渲染后看图）、误差棒数值是否与数据列一一对应。

**自检**：`node -e "import('./eval/violations.mjs').then(m=>console.log(m.detectViolations('\\begin{tikzpicture}\\end{tikzpicture}')))"` 应输出 `[]`；用一条含 `legend pos=north west` 的假代码应检出 `R1_LEGEND_POS`。

### 7.2 `eval/eval.mjs` [MODIFY]

| 改动点 | 位置（2026-09-14 快照） | 内容 |
|---|---|---|
| import 检测模块 | 顶部 import 段（L16-18 附近） | `import { detectViolations } from './violations.mjs';` |
| 计算违例 | 逐用例循环内，`const chartCode = …`（L90）与 `hasCode`（L92）之后 | `const violations = hasCode ? detectViolations(chartCode) : [];` `const violationFree = violations.length === 0;` |
| 计数器 | 循环前的计数器段（L78-81） | 追加 `let zeroViolationPass = 0;` |
| cases 字段 | `cases.push({...})`（L125-133） | 追加 `violations`（数组）与 `violation_free`（布尔） |
| 通过判定 | `if (success && hasCode && compiled) pass += 1;`（L104 附近） | 追加 `if (success && hasCode && compiled && violationFree) zeroViolationPass += 1;` |
| 指标 | `metrics` 对象（L143-150） | 追加 `zero_violation_rate: +((zeroViolationPass / total) || 0).toFixed(3)` 与 `violation_counts`（按 `rule` 计数的对象，如 `{ R3_NODES_PAIRED: 2 }`） |
| 汇总打印 | 汇总段（L165-172） | 追加两行：`零违例率` 与 `违例分布` |
| 逐用例日志 | 用例打印行（L107 附近） | 追加 `viol=${violations.map(v => v.rule).join(',') || 'none'}` |

- 结果 JSON（`eval/results/<tag>.json`，结构 `{tag, run_at, base, cases[], metrics}`）会随之扩展 `cases[].violations` / `cases[].violation_free` 与新增 metric —— **旧结果文件不会被覆盖**（文件名由 `--tag` 决定）。

### 7.3 `eval/cases.json` [MODIFY]

**保留原有 10 条 id 与文案完全不变**（`bar_quarter` / `line_trend` / `pie_share` / `scatter` / `bar_stacked` / `bar_dense` / `error_bar` / `line_multi` / `bar_negative` / `bar_ambiguity`）——批次1 已用这 10 条跑出 `rag-off` / `rag-on` 两组基线，改写会**破坏历史可比性**。

**追加 6 条 V2 用例**（进阶图型 + 歧义 / 缺数据）：

```json
{ "id": "combo_errorbar_multi",  "message": "画一个多系列带误差棒的性能对比柱状图：方案A 520±28，方案B 568±35，方案C 602±22，方案D 585±30" },
{ "id": "dual_axis",             "message": "画一个双 y 轴图：左轴是产量（万吨）、右轴是同比增速（%），2019-2024 年" },
{ "id": "dense_legend_outside",  "message": "画一个 12 个月销售额柱状图，每个柱子都标数值，图例放到图外" },
{ "id": "ambiguous_trend",       "message": "我有一份近几年的人口数据，帮我看看变化" },
{ "id": "missing_data",          "message": "画一个全国主要城市空气质量对比图，我没有具体数据" },
{ "id": "stacked_area",          "message": "画一个 2019-2024 年三款产品累计销量的堆叠面积图" }
```

设计意图：`combo_errorbar_multi` / `dual_axis` / `stacked_area` 压 R5/R1/R3（进阶图型组合）；`dense_legend_outside` 压 R1+R3 交错标注；`ambiguous_trend` / `missing_data` 考察模型在**信息不足**时的图型选择与兜底行为（这两条预期会拉低通过率——**这正是"有区分度"的意义**，不要因为数字变差而删用例）。

### 7.4 运行方式与成本提示

```cmd
cd spring-backend
node eval/eval.mjs --tag=v2
```

> ⚠️ **运行前需确认**：升级后的评估集会触发**真实模型调用**（16 条用例 × 每条一次 chat，含编译轮询），有实际成本与时间开销（参考批次1：10 条约 15s/条）。是否运行、什么时候运行由使用者决定，执行者**不得擅自开跑**。
> 建议对比口径：`--tag=v2-rag-off` 与 `--tag=v2-rag-on`（用 `RAG_ENABLED` 环境变量切换），对比 `zero_violation_rate` 而非只看 `first_pass_rate`——后者在批次1 已饱和。

---

## 8. 回归断言（T7）

`spring-backend/verify.js` 第 2 段，紧跟现有 `responseTime` 断言（2026-09-14 快照约 L168-172）之后追加两条：

```js
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
```

**断言设计原则**：两条都是**类型断言**，允许空数组，因此**不会因为「这段时间没有失败/没有耗时数据」而误报 FAIL**——这是刻意的（避免造假数据去迎合断言）。

**通过数变化**：29 → **31**（批次2 基线 29 含批次2 新增的 1 条 responseTime 断言）。**回归标准仍是 FAIL=0**，通过数是预期变化，回填文档时必须写明新总数。

---

## 9. 任务清单总表

| 任务 | 内容 | 主要落点 | 依赖 |
|---|---|---|---|
| T0 | 前置环境检查 | — | — |
| T1 | `api_log.error_type` 迁移 + 实体字段 | `hello/migrations/`、`entity/ApiLog` | T0 |
| T2 | 分类枚举 + E2 埋点（chat 侧 4 类 + 编译侧 2 类） | `common/ErrorTypes`[NEW]、`ChatService`、`CompileTaskService` | T1 |
| T3 | E2 分类聚合 | `AdminLogService`、`AdminMapper.xml` | T2 |
| T4 | E3 后端：按日耗时序列 | `AdminLogService` | T3 |
| T5 | E3 前端：质量看板（2 卡片 + 2 图表 + 空态三规则） | `src/views/AdminLog.vue` | T4 |
| T6 | 评估集 V2（静态违例检测 + 用例扩充） | `eval/violations.mjs`[NEW]、`eval.mjs`、`cases.json` | T0（与后端解耦，可并行） |
| T7 | verify.js 断言 +2 | `spring-backend/verify.js` | T3、T4 |
| T8 | 总验收 + 文档回填 | 全部 | T5、T6、T7 |

> T6 只依赖 T0，可与 T1–T5 **并行**（互不触碰同一文件）；若并行，注意 T8 的验收顺序。

---

## 10. 总验收清单（全部通过才算批次3 完成）

| # | 验收项 | 方法 | 通过标准 |
|---|---|---|---|
| 1 | 基线不回归 | `scripts\build.cmd && scripts\verify.cmd` | FAIL=0（PASS 预期 **31**） |
| 2 | 分类列存在 | `SHOW COLUMNS FROM X.api_log LIKE 'error_type';` | 有 `error_type varchar(32) NULL` |
| 3 | 上游错误分类落库 | 临时把 `DEEPSEEK_API_KEY` 改成非法值后发起一次生成（或断开网络），再查库 | 新增一行 `call_status=failed` 且 `error_type=UPSTREAM_API_ERROR`；测完**改回真值并重启** |
| 4 | 空回复分类落库 | 见下方「造数说明」 | 若自然发生：`error_type=EMPTY_REPLY`；未发生则如实记为「未覆盖」 |
| 5 | 编译失败分类落库 | 启动前 `set "XELATEX=no-such-xelatex"`（`application.yml` 的 `app.latex.executable: ${XELATEX:xelatex}`）→ 提交一次编译 → 轮询至 failed → 查库 | `GET /api/compile/task/{id}` 终态 failed；`api_log` 新增一行 `error_type=COMPILE_ERROR` 且 `history_id` 非空、`call_error` 含失败信息；**验证完关闭该环境变量重启** |
| 6 | 编译队列满分类 | 需临时把 `compileTaskExecutor` 的 `queueCapacity` 改 1 并二次 rebuild | 提交返回 503；`api_log` 新增 `error_type=COMPILE_QUEUE_FULL`；**改回 100 并 rebuild**（破坏性，可与用户确认后跳过） |
| 7 | **不双计** | 制造一次 `CODE_EXTRACT_FAIL`（见下）或直接查库 | 同一 `history_id` 只多**一行** api_log；`summary.total_calls` 只 +1 |
| 8 | **编译失败只计一条** | 用例 #5 执行后统计 | 一次失败只新增**一行** `COMPILE_ERROR`（若出现两行说明在 `CompileService` 重复落库了） |
| 9 | 失败分类聚合 | `GET /api/admin/log/api-stats` | `errorTypes` 为数组、按 count 降序、次数与库内 `SELECT error_type, COUNT(*) FROM api_log GROUP BY error_type` 一致 |
| 10 | 按日耗时序列 | 同上 | `responseTimeSeries` 元素含 `date/avg/p95/count`，且与 `SELECT DATE(call_time), ROUND(AVG(duration_ms)), COUNT(duration_ms) FROM api_log GROUP BY DATE(call_time)` 对得上 |
| 11 | 前端两图渲染 | `cd hello && npm run serve` → `/admin` 监控页 | 「失败分类分布」与「耗时趋势」两张图正常渲染，日期切换后同步刷新 |
| 12 | **空态三规则** | 把日期区间选到批次2 之前（该区间 `duration_ms` 全为 NULL） | ① 无 0 值柱/空饼图；② 耗时卡片显示「—」而非「0 ms」；③ 显示空态文案 |
| 13 | 评估脚本可跑 | 见下「造数说明」的 dry run | `violations.mjs` 对空代码返回 `[]`、对含 `legend pos=north west` 的样例命中 `R1_LEGEND_POS`；`eval.mjs` 语法自检通过（`node --check`） |
| 14 | 新断言生效 | `scripts\verify.cmd` | 两条新断言 PASS |
| 15 | 文档回填 | §11 清单逐项 | 全部完成 |

**造数说明（诚实口径）**

- **编译类失败（#5、#6、#8）可以稳定构造**：指向不存在的编译器 / 压小队列容量即可。
- **`CODE_EXTRACT_FAIL` 无法稳定构造**：它要求模型「返回了非空内容但既无法解析 JSON、也提不出围栏/裸 tikzpicture 代码」——这依赖上游模型行为，本地不能确定性触发。可选的近似做法：直接 `INSERT` 一行 `api_log(error_type='CODE_EXTRACT_FAIL')` 用于**验证 E3 前端展示**，但必须注明这是**数据构造**，**不等于链路验证**；不得据此声称「代码提取失败分类已通过实测」。
- **`EMPTY_REPLY` 同理**难以稳定构造（上游返回空内容 + 降级链全空）。若在自然运行中观察到，记录证据；否则如实写「未覆盖」。

---

## 11. 回填清单与回归节奏

**回归节奏**

1. 每任务（T1–T7）完成：`mvn -q -DskipTests compile`（前端任务为 `npm run serve` 目视）。
2. 大节回归点：**T3 后**（E2 闭环）、**T5 后**（E3 前后端闭环 + 手工目视）、**T7 后**（断言生效）、**T8 全部完成后**。
3. 前端任务必须**手工目视**（`verify.js` 覆盖不到 UI 空态）。

**文档回填（T8）**

| 文件 | 回填内容 |
|---|---|
| `docs/plan-AI.md` | §4 批次3 行补完成状态（含 `PASS=31 FAIL=0`、评估集 V2 的 `zero_violation_rate` 结果口径） |
| `docs/log.md` | 追加「批次3 落地」小节：E2 七类枚举与两个缺口补齐（空回复未落库、代码提取失败未分类）、E3 看板与空态三规则、评估集 V2 的 `violations.mjs` 与 **R4 不做静态检测的理由**、造数诚实口径、回归数字 |
| `README.md` §4.2 | 数据库初始化命令追加 `migrations/alter_api_log_error_type.sql` |
| `docs/deployment.md` §2.5 | 迁移清单追加同一文件 |
| `docs/architecture.md` | §5.2 表说明行补 `error_type`（并把 `api_log` 表述更新为「AI 链路失败事件日志」）；§5.3 迁移顺序追加该文件 |
| `docs/openapi.yaml` | `/api/admin/log/api-stats` **已定义**（约 L1171 起），但其响应 schema 是 `type: object` + `additionalProperties: true`（无字段清单）。本批**不**逐字段展开（会与 Java 代码重复维护），只在该端点 `description` 补一句：「响应含 summary / timeSeries / recentFailures / responseTime / errorTypes / responseTimeSeries」 |
| `spring-backend/README.md` | §7.1 verify 覆盖描述补「失败分类/耗时序列断言」，最近一次结果改为 `PASS=31 FAIL=0` |

---

## 附录 A：可外发分工建议

沿用批次1/2 的分工共识（独立零依赖 / 前端单文件 / 纯文档类可外发；核心 Java 链路留 CodeBuddy）：

| 任务 | 规格来源 | 约束 |
|---|---|---|
| T5 前端质量看板 | 本文档 §6 | **单文件改动**（只改 `AdminLog.vue`）；空态三规则必须实现；禁改其他文件 |
| T6 评估集 V2 | 本文档 §7 | 只在 `eval/` 目录内；零第三方依赖；禁改 Java；R4 明确不做 |
| 迁移清单同步（§11 的 README/deployment/architecture 三处） | 本文档 §11 | 纯文档 |
| T1–T4、T7 核心 Java 链路与回归断言 | 本文档 §2–§5、§8 | 留 CodeBuddy（涉及事务性落库口径与聚合正确性） |

---

## 附录 B：本批发现的偏差（**记录，不在本批修改**）

1. **`AdminLog.vue` 缺少实例清理**：`chartInstance`（`let chartInstance = null`，L283）在 `initChart()`（L394-406）里创建并挂了 `ResizeObserver`，但全文**没有** `onUnmounted` / `dispose()` / `disconnect()`（已核实）。本批新增 2 个实例会**放大**该泄漏面；建议后续批次补一个统一的图表实例清理（本批不修，避免扩大改动面）。
2. **`.chart-container` 是空类名**：模板 L71 有 `div.chart-container`，但 `<style scoped>`（L699-904）内**没有**对应选择器（已核实），属历史遗留，本批不动，新样式也不要依赖它。
3. **`api_log` 的语义已扩展**：从「LLM 调用日志」变为「AI 链路失败事件日志」（新增编译类分类）。§11 已要求同步 `architecture.md` 的表说明；其余历史文档（含批次1/2 施工图）保持原样不动。

---

## 附：与 `plan-AI.md` 的映射

| 本文档 | plan-AI.md |
|---|---|
| §2–§4 E2 | §3 任务表 E2「失败归类统计」、§8「E2 失败归类」 |
| §5–§6 E3 | §3 任务表 E3「质量看板」、§8「E3 质量看板」 |
| §7 评估集 V2 | §8「评估集 V2（批次 3 前置建议）」 |
| §8 断言 / §10 验收 | §11 验收与回归 |

---

## 执行记录与偏差（2026-09-14 执行后回填）

**结果**：T1–T7 全部落地，`scripts\build.cmd && scripts\verify.cmd` → **PASS=31 FAIL=0 WARN=0**。迁移 `alter_api_log_error_type.sql` 已应用；前端生产构建通过；COMPILE_ERROR 造数实测通过（3 次失败各落 1 行、`total_calls` 每次 +1、`prompt_version=NULL`）。

**执行中修正的三处偏差（施工图原稿已不准确，以此处为准）**：

1. **耗时口径必须排除编译类行（重要）**：§5.1 只写了「按日聚合 `duration_ms`」，未考虑批次3 起 `api_log` 同时存了 LLM 与编译两类耗时。首版实测把编译失败的 `duration_ms=26` 算进 `responseTime`，把 avg 从 3157 拉低到 1592（`sample_count` 2）。已加 `isCompileFailure()` 过滤：**`responseTime` 与 `responseTimeSeries` 只统计生成链路（LLM）**，与批次2「两段耗时不混算」一致。因此 §10#10 的核对口径应改为「**排除 `error_type in (COMPILE_ERROR, COMPILE_QUEUE_FULL)` 后**与 DB 聚合对齐」。
2. **附带修复：编译失败信息为空**：`LatexCompiler.run` 的 `catch (Exception e)` 原本吞掉 `ProcessBuilder` 异常，「找不到 xelatex」时 `output` 为空 → 任务 `error` 与 `api_log.call_error` 双双空白。已在该 catch 追加 `xelatex 执行异常: <msg>`（仅补错误信息，未改编译/超时/清理逻辑）。§0#6「编译逻辑不动」按此理解：**逻辑不变，允许补错误可观测性**。
3. **R7 检测范围收窄**：§7.1 原写「②全文含全角 `，`」，实测会误伤中文标签里的正常标点（如「数据来源：国家统计局，2024」）。已收窄为**只检查 LaTeX 结构上下文**（`symbolic x coords={…}` 与 `coordinates {…}` 花括号内），自检确认中文正文逗号零误报。

**验收执行记录（2026-09-14 追加）**

- **评估集 V2 实跑**：rag-on 轮 16 例 → 生成成功率 / 可编译率 / 首轮通过率均 **1.0**、**零违例率 1.0**（avg 16871ms / p95 35916ms）；rag-off 轮因 **DeepSeek 余额不足（`Insufficient Balance`）作废**，`eval/results/v2-rag-off.json` 已写 `invalid_reason`，待充值后重跑 `--tag=v2-rag-off` 覆盖。
- **R8 检测器误报修正（本施工图 §7.1 的 R8 规则描述需据此修正）**：首轮跑出 2 条 `R8_LABEL_STAGGER`（`bar_stacked` 堆叠柱图、`bar_negative` 单系列柱图），逐条比对代码后确认**均为误报**——R8 原文只针对「折线/曲线、≥2 系列且 X≥8」。已按规则原文收窄实现（用 `\addplot` 计系列数 + 估 X 点数 + 排除 `ybar`），再用**重放**（对已落盘 `chart_code` 重跑检测器、零模型调用）把 `zero_violation_rate` 修正为 1.0。
- **前端质量看板目视验收（已完成）**：浏览器自动化实测——默认态两图正常渲染、卡片 `8744 ms / 35216 ms / 样本 33` 与 SQL 交叉验证一致；切到无数据区间（2026-09-13）后三条空态规则全通过（`qualityCanvas=0` 不画图、卡片显示「—」而非 0、两条空态文案出现）。
- **副产品**：rag-off 轮 16 次失败被如实落库为 **16 条 `UPSTREAM_API_ERROR`**，使 §10#3 的上游错误类从「未覆盖」升级为「有真实自然样本」。
- **仍开放**：`COMPILE_QUEUE_FULL`（需临时改 `queueCapacity`，未做）；`EMPTY_REPLY` / `CODE_EXTRACT_FAIL`（仍无稳定造数手段，未声称通过）；rag-off 基线（待充值）。

