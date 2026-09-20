# 优化 / 修复待办清单

> 生成日期：2026-09-20。来源：`docs/compile-chart-issues.md` §四、`docs/log.md` 剩余盲区（2026-09-18 口径）、代码核查。
> 已闭环事项（G1 异步编译契约、并发临时目录竞态、字面 `\n` 转义还原、编译队列满等）见 `docs/log.md`，不在此清单内。
> 勾选约定：`[ ]` 待办　`[~]` 进行中　`[x]` 完成（完成后在行尾追加日期）。

---

## P0 — 核心功能收益明确

- [ ] **1. RAG 语料定级收尾**：账号 4/15/16 共 **332 条**历史仍为 `unverified`、不参与召回（log.md 盲区 5）。
  `verify` 可重复执行、可随时重算，属纯执行性工作；完成即提升召回覆盖率，且是 #12 消融实验的前置条件。
- [ ] **2. 单次生成质量信号**：`feedback` 表是通用功能反馈，**与 history 无关联、无评分**（log.md 盲区 2）。
  在每条 AI 回复加"有用 / 重新生成"轻量评价并存表（关联 `history_id`），为 RAG / 提示词效果评估积累真实数据。
- [ ] **3. 补 CI 流水线**：`.github/workflows` 不存在。
  最小化即可：`mvn test` + `npm run lint` + `npm run build`，替代目前靠手动全量测的回归方式。

## P1 — 已知缺陷，修法明确

- [ ] **4. 饼图 `\pie` 递归溢出无确定性兜底**（C4 🟡，compile-chart-issues.md）。
  现仅靠提示词 + 重跑；可在 `preprocess` 加一条规则：检测 `\pie` 环境内的 `\node at (axis description cs:…)`，剥离或搬到图外（可复用 P9 `normalizeSourceNote` 的搬运逻辑）。
- [ ] **5. `verified` 视觉盲区**（log.md 盲区 1）：R4 量纲、标注/图例视觉重叠**无自动判据**，"编译成功 + 静态零违例"仍可能收进错图（477 空图、490 白纸类均会被误判 `verified`）。
  可选方向：编译后 pdf→png 像素启发式（留白比例、bbox 重叠）；短期先把人工抽检脚本化抽样。
- [ ] **6. 管理端反馈统计是临时 hack**（`src/views/AdminFeedback.vue:512`，用列表接口凑数据）。后端补一个 stats 聚合接口。
- [ ] **7. `DB_PASSWORD` 缺省 `000` 且 prod 无校验**（`spring-backend/src/main/resources/application.yml:25`）。
  参照 `JwtTokenProvider.java:39` 的 fail-fast 范式，在 prod profile 下强制要求显式配置。
- [ ] **8. 示意数据不可自动识别**（log.md 盲区 3）：`data_source` 只有 `dataset` / `no-dataset` 两态，"使用公开统计"与"模型自拟示意数据"无法区分，影响语料可信度分级。
- [ ] **9. 环境类失败被保守判为"未验证"**（log.md 盲区 4）：`COMPILE_ERROR` 同时覆盖 LaTeX 报错 / xelatex 执行异常 / 任务中断，环境类失败导致语料少收录。方向安全，可在 `generation_path` 细分失败类型后再放宽。

## P2 — 已记录取舍，按需推进

- [ ] **10. 空白 PDF 判定未彻底解耦**（compile-chart-issues.md §四）：现靠"< 2048 字节"启发式（2026-09-18 落地），≥2KB 的空白图仍可能漏判。
- [ ] **11. `\%` 转义（R12）无预处理兜底**：正则替换文本参数里的裸 `%` 有误伤风险，维持提示词约束可接受。
- [ ] **12. 提示词 / 检索消融实验**（log.md 盲区 6）：刻意延后，须等 #1 语料定级完成再做，否则结论被脏语料污染。
- [ ] **13. Q6 密集柱缩写后约 1pt 碰触**（compile-chart-issues.md §四）：字体物理下限，再压缩需去掉"万"后缀或降数字精度；无用户投诉不动。
- [ ] **14. 登录页"记住我 / 忘记密码"未实现**（`src/components/LoginForm.vue:42`，整块注释）：功能补全非缺陷，按交付范围决定。
- [ ] **15. 前端零自动化测试**：后端已有 79 例单测（`spring-backend/src/test`），前端无。成本最高、当前阶段收益一般，排最后。

## T — 技术演进（补齐真实缺口，非装饰性引框架）

> 决策依据：`docs/plans/plan-AI-LangChain4j-评估与落地方案.md`——P1–P6 原语已自研且逐行可解释，**不替换**；只补缺失能力。全项目当前无 Redis、无 SSE/WebSocket。

- [ ] **T1. SSE 流式输出（`SseEmitter`）**：现 AI 回复为整段返回，用户干等十几秒。
  改为流式打字机 + 停止按钮。改动集中在 `ChatController` / `ChatService` + 前端 fetch 读流；注意 Nginx `proxy_buffering off` 与断连清理。约 1–2 天。
- [ ] **T2. Redis**：三处真实痛点——
  ① 邮箱验证码现存 MySQL 表（`EmailVerificationCode`），改 Redis TTL 天然契合；② 编译并发控制是**进程内** `Semaphore`（单实例限定），改 Redis 分布式信号量才撑得起多实例叙事；③ 登录 / 生成接口无限流，用 Redis 计数器补上。
- [ ] **T3. P7：tool-calling / 多步 agent 循环**（评估文档 §2.5 定位的唯一缺失原语）：
  形态如"生成 → 编译失败 → 读 log 自动修 → 重编译"闭环，接在现有编译队列上。
  路线二选一（见评估文档 §2.6/§2.8）：自研 ~130 行扩展 `LlmClient`（工程性价比优）vs LangChain4j 并存式最小接入、默认关闭（简历关键词优）。
- [ ] **T4. Docker Compose + Nginx 容器化**：`deployment.md` 现为手工流程，容器化补齐"会部署"一环，成本低。

### 明确不做（防止为简历而简历）

- pgvector / Milvus：评估文档 §3.6 已否决，当前数据量下 MySQL 暴力余弦即正解；面试可口头讲演进方案，不实际替换。
- 微服务拆分、消息队列：无多实例前提，追问即露底。
