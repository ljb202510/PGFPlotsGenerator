# PGFPlotsGenerator 开发者指南

> **定位**：面向参与本项目开发的工程师（含 AI 编码助手）。覆盖本地环境搭建、代码结构导览、编码约定与二次开发流程，快速实用版。
> **前置阅读**：`docs/architecture.md`（模块/数据流/ER 详图，本文不重复堆砌）；`README.md`（API 明细）。
> **协作规则**：`.github/copilot-instructions.md`（AI 代理必须遵守的仓库指引）。

---

## 1. 本地环境搭建

### 1.1 前置依赖

| 依赖 | 版本建议 | 用途 |
|---|---|---|
| JDK | 17+（本机 JDK 21） | 后端构建与运行（Maven） |
| Node.js / npm | ≥ 16 | 前端构建（后端已改用 Java，Node 仅服务前端工具链） |
| MySQL | 5.7+ | 库名 `X` |
| XeLaTeX（TeX Live） | 完整版 | 编译 PDF（本地 Windows 可装 TeX Live/TeXstudio，log.md：下载 arm 版会导致无法运行） |

### 1.2 初始化数据库（Windows 示例，密码见 `data/.env`）

```bash
cd hello
mysql -u root -p        # 输入密码 000
mysql> source 1.sql;                                    # 建库 X + 8 张表 + 预置管理员 admin123/666666
mysql> source migrations/add_notice_feedback_columns.sql;   # notice 定向/反馈字段迁移
mysql> source migrations/create_notice_read_table.sql;      # notice_read 表
mysql> source migrations/create_conversations_tables.sql;   # conversations / conversation_messages（自 Node 迁移脚本归档）
mysql> source migrations/alter_api_log_prompt_version.sql;  # api_log 加 prompt_version
mysql> source migrations/alter_api_log_duration_ms.sql;     # api_log 加 duration_ms
mysql> source migrations/alter_api_log_error_type.sql;      # api_log 加 error_type
mysql> source migrations/create_rag_vector.sql;             # rag_vector 向量表（RAG 用）
mysql> source migrations/alter_rag_vector_quality.sql;      # rag_vector 加 quality 分级列
mysql> source migrations/alter_data_file_data_name.sql;     # data_file.data_name 扩至 50
mysql> exit;
```

> 全部迁移执行完共 **12 张表**。完整说明见 `README.md` §4.2。

> 提醒：`1.sql` 首行 `DROP DATABASE IF EXISTS X;` 会清库重建，仅首次/重建使用。

### 1.3 双终端启动

```bash
# 终端 A：前端（Vue CLI，默认 http://localhost:8080）
cd hello
npm install
npm run serve

# 终端 B：后端（Spring Boot，默认 :3000；脚本自动选 JDK 17+）
cd hello/spring-backend
scripts\mvn-run.cmd    # 开发模式（mvn spring-boot:run，热改 Java 需重启进程）
# 或 scripts\run.cmd（启动 jar，jar 不存在则先构建）
```

后端配置自动读取 `../data/.env`（JWT_SECRET 缺失会启动失败；SMTP/AI 密钥按需补填）。

### 1.4 验证

- 打开 `http://localhost:8080` → 注册/登录页正常
- 后端控制台输出 `Started PgApplication`（Tomcat :3000）
- 用 `admin123/666666` 管理员登录；用户走邮箱验证码注册（需配好 SMTP）

## 2. 代码结构导览

```
hello/
├── 1.sql                      # 基础建表（users/data_file/generation_history/api_log/feedback/system_log/email_verification_codes/notice）
├── migrations/                # SQL 迁移（notice 字段、notice_read、conversations 两表归档、api_log 扩展列、rag_vector、data_name 扩列）
├── docs/                      # 文档（README 补充：architecture / deployment / development / openapi.yaml / log / test / process）
├── public/                    # 前端模板（npm run build 产物输出到 dist/，不入库）
├── src/                       # ★ 前端 Vue 3 SPA
│   ├── main.js                # 入口：ElementPlus/Vuex/router/全局 UI 组件注册 + ResizeObserver 防抖补丁
│   ├── config.js              # API_BASE_URL（前后端地址的唯一事实源）
│   ├── App.vue                # 布局外壳：按登录态渲染 TheAuth / 用户主区 / 管理员主区
│   ├── router/index.js        # 14 条平级路由（懒加载；无全局守卫）
│   ├── store/index.js         # Vuex：currentUser/isAuthenticated/unreadCount
│   ├── components/            # TheAuth、Login/Register/AdminLogin、Common/Admin Navbar+Sidebar、ui/ 通用组件
│   └── views/                 # ChartGenerator、MyHistory、DataUpload、MyFeedback、MyNotice、ChangeInformation、Admin* 共 10 页
├── spring-backend/            # ★ 后端 Spring Boot（唯一后端）
│   ├── PgApplication.java     # 入口；SecurityConfig 统一拦截（/api/admin/** 要求管理员角色）
│   ├── controller/            # 13 个控制器（路径与退役的 Express 版一致）
│   ├── service/               # 业务服务（ChatService 三通道降级链、VerificationService 邮件+验证码、CompileTaskService 异步编译队列…）
│   │   └── rag/               # RAG：EmbeddingClient / VectorStore / Retriever / PromptComposer / RagService
│   ├── mapper/                # MyBatis-Plus Mapper（复杂 SQL 在 resources/mapper/*.xml）
│   ├── security/              # JwtTokenProvider / JwtAuthenticationFilter（角色查库装配）
│   ├── util/                  # LatexCompiler（30s + preprocess 12 条确定性修复）/ ChartCodeExtractor / ChartCodeValidator / StructuredOutputParser / PromptTemplates（R1–R16）/ SystemLogWriter / FileStorage / FileContentReader
│   ├── tools/                 # RagCli / RagTemplates（内置模板库 SEEDS）
│   ├── scripts/               # build / run / mvn-run / verify / rag_seed / rag_demo / rag_backfill / rag_purge（.cmd）
│   ├── verify.js              # 全量接口回归脚本（verify.cmd 调用）
│   ├── eval/                  # 离线评估集（cases.json 16 例 + eval.mjs + violations.mjs + results/）
│   └── application.yml        # 默认配置；自动导入 ../data/.env
└── data/                      # 共享运行时数据目录（原 backend/ 改名而来，请勿删除）
    ├── .env                   # 环境变量（Java 启动时自动读取，勿提交 .env）
    ├── uploads/               # 数据集文件
    └── storage/               # history/{uid}/{id}.json、generated_charts/user{uid}/hist{id}.pdf（仅服务端内部读写，经鉴权接口访问）
```

对应模块/路由/数据流细节见 `docs/architecture.md` §3–§5。

## 3. 编码规范与约定

> 前端走 Vue CLI ESLint（`npm run lint`）；后端遵循 Spring 分层约定（见 §3.2）。

### 3.1 通用约定

- 全库为**参数化查询**（`?` 占位），禁止字符串拼接 SQL（防注入）。
- **数据隔离**：所有按用户查询/删除必须带 `user_id = ?` 条件；越权一律返回 404/403。
- 业务代码中的异常/越权统一调 `SystemLogWriter`（`util/SystemLogWriter.java`）记录，message 会被截断 500 字；该工具只作为副作用，失败不影响主流程。
- 密码规则（`AuthService` 校验）：**仅字母数字、长度 6–16 位**；存取用 BCrypt（`BCryptPasswordEncoder`）。
- 响应风格：Java 后端（唯一后端）所有 JSON 接口已统一 `{ success, data, message }`（`common/Result.java` + `GlobalExceptionHandler`）；原 Node 版的 `{code,message,data}` 风格已随退役收敛，勿再引入。

### 3.2 后端约定

- **分层**：新增接口按 `controller/ → service/ → mapper/` 分层（复杂 SQL 写 `resources/mapper/*.xml`）；控制器返回 `Result.ok(...)`，业务错误抛 `BusinessException`（`notFound`/`forbidden`/`badRequest` 等静态工厂），由 `GlobalExceptionHandler` 统一转 `{success,data,message}` 与对应状态码。
- **鉴权**：需登录接口由 `SecurityConfig` 统一拦截；`/api/admin/**` 自动要求管理员角色；非该前缀的管理员接口用 `@PreAuthorize("hasRole('ADMIN')")`（角色查库装配，不信任 JWT 中的角色声明）。
- **数据隔离**：所有按用户查询/删除必须带 `user_id` 条件（`LambdaQueryWrapper.eq`）；越权一律 404/403。
- 删除级联等跨表操作使用**事务**（service 方法加 `@Transactional`，参照 `HistoryService`、`ConversationService`）。
- 编译等外部命令用 `util/LatexCompiler`（ProcessBuilder，30s 超时 + 临时目录必清理）。

### 3.3 前端约定

- **组件/页面**：路由懒加载 `() => import('../views/SomeView.vue')`（`.github/copilot-instructions.md`）；页面放 `views/`、复用组件放 `components/`（通用 UI 放 `components/ui/`）。
- **状态/认证**：token 存 `localStorage` 或 `sessionStorage`（以 token 存在与否二选一）；user 经 Vuex `SET_USER` 持久化。取 token 模式参照 `ChartGenerator.vue` 的 `getAuthToken()`。
- **API 基址**：一律从 `src/config.js` 引 `API_BASE_URL`（勿再硬编码地址/忘写反引号拼接——曾因此导致联调失败）。
- **样式**：全局设计令牌在 `src/styles/tokens.css`（`--brand`/`--bg-*`/`--radius-*` 等），组件用变量，勿散点硬编码色值。
- 图标用 `@element-plus/icons-vue`，不用 emoji/第三方图标库。

## 4. 二次开发：新增一个功能/接口（示例：新增「收藏图表」）

### 4.1 后端（5 步）

1. **建表/迁移**：给 `generation_history` 加列，写 `migrations/add_favorite_column.sql`（带存在性判断，参照 `add_notice_feedback_columns.sql`）并执行；或新建表；同时在 `entity/` 补实体/字段。
2. **写服务** `service/FavoriteService.java`：

```java
// service/FavoriteService.java —— 收藏功能示例
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final GenerationHistoryMapper historyMapper;

    public void toggleFavorite(int userId, int historyId, boolean favorite) {
        LambdaUpdateWrapper<GenerationHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GenerationHistory::getHistoryId, historyId)
               .eq(GenerationHistory::getUserId, userId)
               .set(GenerationHistory::getIsFavorite, favorite ? 1 : 0);
        if (historyMapper.update(null, wrapper) == 0) {
            throw BusinessException.notFound("历史不存在或无权操作");
        }
    }
}
```

3. **写控制器** `controller/FavoriteController.java`：

```java
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PutMapping("/{historyId}")
    public Result<Void> toggle(@PathVariable int historyId, @RequestBody Map<String, Object> body) {
        // 用户身份经 SecurityUtils 取当前登录用户，数据按 user_id 隔离
        favoriteService.toggleFavorite(SecurityUtils.userId(), historyId, Boolean.TRUE.equals(body.get("isFavorite")));
        return Result.ok(null);
    }
}
```

4. （若跨表/多步）在 service 内加 `@Transactional` 实现。
5. 验证：`mvn -DskipTests package`；用 curl/Postman 带 Bearer token 调 `PUT /api/favorite/1`。

### 4.2 前端（4 步）

1. `src/views/MyHistory.vue`（或 ChartGenerator.vue）按现有模式调用：

```js
import axios from 'axios';
import { API_BASE_URL } from '@/config';

async function toggleFavorite(historyId, isFavorite) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token');
  const { data } = await axios.put(`${API_BASE_URL}/api/favorite/${historyId}`,
    { isFavorite },
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!data.success) throw new Error(data.message);
}
```

2. 需要跨页面共享则加 Vuex mutation/action（`src/store/index.js`）。
3. UI 组件复用 `components/ui/`（AppButton/EmptyState/AppSpinner 等）与 Element Plus。
4. 验证：页面操作 + 检查库中 `is_favorite` 更新、错误提示可用。

## 5. 常用命令清单

```bash
# 后端（脚本自动选 JDK 17+）
cd hello/spring-backend && scripts\mvn-run.cmd   # 开发模式 mvn spring-boot:run
cd hello/spring-backend && scripts\run.cmd       # 启动 jar（jar 不存在则先构建）
cd hello/spring-backend && scripts\verify.cmd    # 全量接口回归（最近一次 PASS=42 FAIL=0 WARN=0）
cd hello/spring-backend && scripts\rag_seed.cmd  # RAG 模板库初始化（另有 rag_demo/rag_backfill/rag_purge）
cd hello/spring-backend && mvn test              # 单测 79 例
# 前端
cd hello && npm run serve              # 开发服务器 :8080
cd hello && npm run build              # 产物 dist/
cd hello && npm run lint
# 数据库
mysql -u root -p X < 1.sql             # 重建（危险，会 DROP）
# 编译验证（手动）
cd <temp> && xelatex -interaction=nonstopmode test.tex
```

## 6. 常见开发易错点

- 改 `data/.env`/`config.js` 后需重启对应进程（Java 后端需重启应用，前端改 config 需刷新）。
- 新增表后忘执行 `migrations/` 下迁移 SQL，接口会报「table doesn't exist」。
- `uploads`、`storage` 目录不存在时上传/编译会失败——Java 启动时会自动创建目录，若权限不足需手工 mkdir。
- 删除用户/数据走 AdminUser/MyHistory 接口（内部级联），避免直接 SQL 触发外键错误。
- 对话持久化只有传 `conversation_id` 才会写入（`service/ChatService`）；纯单轮生成不会进 `conversation_messages`。

## 7. Java 后端（spring-backend）开发

Spring Boot 后端为本项目**唯一后端**（原 Node 版已退役删除，接口路径与行为等价），开发约定如下（详见 `spring-backend/README.md`）。

### 7.1 环境与启动

```bash
# 需 JDK 17+（本机 JDK 21）
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"
cd hello/spring-backend
mvn -DskipTests package
set "JWT_SECRET=your-super-secret-jwt-key-change-me"   # 缺失即启动失败
java -jar target/pgfplots-backend-1.0.0.jar            # 或 mvn spring-boot:run
```

数据库沿用同一个库 `X`（Java 迁移本身无需重跑 `1.sql`，但需按 §1.2 补齐 `migrations/` 下的全部迁移，当前共 12 张表）；`DB_*` 缺省回退 `localhost/root/000/X`（`application.yml`）。

### 7.2 新增一个接口（示例）

1. **建表/迁移**：写 `migrations/*.sql`；实体放 `entity/`，Mapper 放 `mapper/`（复杂 SQL 写 `resources/mapper/*.xml`）。
2. **服务**：业务放 `service/`；跨表删除加 `@Transactional`；按 `user_id` 隔离（`LambdaQueryWrapper.eq`）。
3. **控制器**：放 `controller/`，返回 `Result.ok(...)`；业务错误抛 `BusinessException`，由 `GlobalExceptionHandler` 统一转 `{success,data,message}` 并给出状态码。
4. **鉴权**：需登录接口默认被 `SecurityConfig` 拦截；`/api/admin/**` 自动要求管理员；非该前缀的管理员接口用 `@PreAuthorize("hasRole('ADMIN')")`。
5. **验证**：`mvn -DskipTests package` 后按 `spring-backend/README.md` §7 冒烟。

### 7.3 约定

- 字段名保持既有契约（与退役的 Express 版一致，DTO 用 `@JsonProperty` 输出 snake_case），减少前端改动。
- 系统日志用 `SystemLogWriter`（副作用式，不抛异常）；外部命令用 `util/LatexCompiler`（30s 超时 + `safeCleanup`）。

---
**文档版本**：1.3　**基准日期**：2026-09-18　**交叉引用**：docs/architecture.md、README.md、.github/copilot-instructions.md
