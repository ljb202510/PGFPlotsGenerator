# 批次 1 可执行执行方案（A1 RAG + A2 Prompt 工程化 + A3 结构化输出 + E1 离线评估）

> **本文档是自包含的施工图**：执行者（人或 AI 编码助手）拿到本文档即可直接动工，无需回读 `plan-AI.md`。
> 项目：PGFPlotsGenerator 后端（`hello/spring-backend/`，Spring Boot 3.2.12 + Java 17 + MyBatis-Plus 3.5.5 + MySQL）
> 前置：第一阶段 Java 迁移已完成，`verify.cmd` 基线 **PASS=27 / FAIL=0**。
> 日期：2026-09-13
>
> **执行进度（2026-09-13 晚更新）**：
> - ✅ T0–T11 + T13（A1 RAG + A2 + A3 + CLI）**已全部实现并通过实测验收**，执行记录见 §9 顶部「验收执行记录」，详细过程见 `docs/log.md` 第八章「批次1 主链路落地」；
> - ⏳ T12（E1：`eval/cases.json` + `eval.mjs`，即 §8 章节）**待执行，已留给 Trae**；完成后回填 §12 的 rag-off/rag-on 对比数字；
> - 实测调参：`min-score` 默认 0.72 → 0.55（`${RAG_MIN_SCORE}` 可覆盖），模板 embedText 改自然句式，历史行 title 已修复——§5 相关骨架以实际代码为准。

---

## 0. 给执行者的硬性约束（先读，违反即返工）

1. **不引入任何 AI 框架**：禁止 LangChain4j / Spring AI / pgvector / Milvus / 任何向量库中间件。RAG 全部自研约 200 行，MySQL 存向量（JSON 文本）+ Java 暴力余弦。
2. **零破坏原则**：`RAG_ENABLED=false`（默认值）时，系统行为必须与改造前**逐字节一致**——同一请求返回相同的 prompt（抓 prompt 日志对比）、相同响应结构。
3. **静默降级**：RAG / embedding 的一切异常（未配置、超时、报错、维度不符）只允许记 `system_log` warning，**绝不抛出阻断主流程**。
4. **每完成一个任务节**跑一次编译自检；**每完成一个大节（A1 / A2+A3 / E1）跑一次 `scripts/verify.cmd`，PASS 必须仍是 27 / FAIL=0**。
5. 代码风格必须对齐现有约定（已探明）：
   - 注入一律 `@RequiredArgsConstructor` + `private final`；
   - 副作用写库（api_log / system_log / rag_vector）全部 try-catch 吞异常不阻断主流程；
   - 日志中文 + `[TAG]` 前缀（如 `[RAG]`），与现有 `[CHAT]` / `[LLM]` 风格一致；
   - 工具类 `public final class` + 私有构造器 + static 方法；
   - Javadoc 中文注释，说明对应改造点。
6. **明确不做**（本批次范围外，禁止顺手做）：A4 大数据集截断、G1 编译队列、流式输出、多模型路由增强、限流、缓存。

---

## 1. 前置环境检查（任务 T0，约 10 分钟）

依次执行，任何一项失败先修复再继续：

| # | 检查项 | 命令 | 通过标准 |
|---|---|---|---|
| 1 | JDK 17 可用 | `java -version` | 17.x |
| 2 | MySQL 可达，库 `X` 存在 | 连 `localhost:3306`，账号 `${DB_USER:root}/${DB_PASSWORD:000}` | `SHOW TABLES;` 有 `generation_history`、`api_log` |
| 3 | 当前基线绿 | `cd hello/spring-backend && scripts\build.cmd && scripts\verify.cmd` | 输出 `PASS=27 FAIL=0 WARN=0`（邮件项 SKIP 不计 FAIL） |
| 4 | 环境变量存在 | 检查 `../data/.env` 或系统环境变量 | `DEEPSEEK_API_KEY` 或 `NSCC_API_KEY` 至少一个非空 |

> 注意：`application.yml` 通过 `spring.config.import: optional:file:../data/.env[.properties]` 共享环境文件，后端必须在 `spring-backend` 目录下启动；`verify.js` 已自动处理。

---

## 2. 数据库迁移（任务 T1，约 15 分钟）

增量迁移统一放 `hello/migrations/`（现有惯例：`create_conversations_tables.sql` 等都在此目录）。新建 **2 个文件**：

### 2.1 `hello/migrations/create_rag_vector.sql`

```sql
-- 批次1/A1：RAG 向量存储表（MySQL 存 JSON 向量 + Java 暴力余弦，无向量库中间件）
USE `X`;

CREATE TABLE IF NOT EXISTS rag_vector (
  vector_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type ENUM('history','template') NOT NULL,  -- history=用户历史成功案例；template=进阶图型模板库
  user_id     INT NULL,             -- history 归属用户（严格隔离）；template 为 NULL
  ref_id      INT NOT NULL,         -- history → generation_history.history_id；template → 模板编号(1..N，seed 脚本固定顺序)
  title       VARCHAR(255),         -- 展示标题（面试演示用）
  embed_text  MEDIUMTEXT NOT NULL,  -- 参与向量化的文本
  content     MEDIUMTEXT NOT NULL,  -- 召回后注入提示词的片段（tikz 代码等）
  embedding   LONGTEXT NOT NULL,    -- JSON 数组文本，如 [0.12,-0.33,...]
  dim         INT NOT NULL,         -- 维度一致性校验（换模型后可识别需重建）
  model       VARCHAR(64) NOT NULL, -- embedding 模型名（同上）
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_ref (source_type, ref_id),
  INDEX idx_user (user_id),
  INDEX idx_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.2 `hello/migrations/alter_api_log_prompt_version.sql`（A2 用）

```sql
-- 批次1/A2：api_log 记录提示词版本，支持「同一用例换版本对比」
USE `X`;

ALTER TABLE api_log
  ADD COLUMN prompt_version VARCHAR(32) NULL AFTER call_error;
```

**自检**：执行两个 SQL 后 `DESC rag_vector;` 与 `DESC api_log;` 字段齐全；重复执行 CREATE 不报错（IF NOT EXISTS）。

> 同步修改实体：`ApiLog.java` 新增字段 `private String promptVersion;`（靠驼峰自动映射，无需 `@TableField`）。

---

## 3. 配置层（任务 T2，约 30 分钟）

### 3.1 `config/AppProperties.java` 新增两个嵌套类（对齐现有 `@Data` + 默认值风格）

```java
/** RAG 检索增强配置（批次1/A1）：全链路可关，关闭时行为与旧版一致 */
@Data
public static class Rag {
    /** 总开关：false 时 RagService 所有方法直接短路返回，零开销 */
    private boolean enabled = false;
    /** 历史案例召回条数 */
    private int topKHistory = 3;
    /** 模板召回条数 */
    private int topKTemplate = 2;
    /** 余弦相似度阈值，低于该值丢弃 */
    private double minScore = 0.72;
    /** embedding 单次调用超时（毫秒） */
    private long timeoutMs = 3000L;
    /** 检索时最多加载的历史向量条数（防全表膨胀） */
    private int historyLimit = 500;
    /** 检索时最多加载的模板向量条数 */
    private int templateLimit = 200;
}

/** OpenAI 兼容 /v1/embeddings 配置（如 SiliconFlow BAAI/bge-m3 或阿里百炼 text-embedding-v4） */
@Data
public static class Embedding {
    private String apiKey;
    private String apiUrl = "https://api.siliconflow.cn/v1";
    private String model = "BAAI/bge-m3";
    /** 向量维度，用于写入与校验一致性（bge-m3 / text-embedding-v4 均为 1024） */
    private int dim = 1024;
}
```

在 `AppProperties` 顶层类中新增字段（对齐现有 `private Latex latex = new Latex();` 风格）：

```java
private Rag rag = new Rag();
private Embedding embedding = new Embedding();
```

### 3.2 `application.yml` 新增（沿用 `${ENV:default}` 风格，追加在 `app.mail` 之后）

```yaml
  rag:
    enabled: ${RAG_ENABLED:false}
    top-k-history: 3
    top-k-template: 2
    min-score: 0.72
    timeout-ms: ${RAG_TIMEOUT_MS:3000}
    history-limit: 500
    template-limit: 200
  embedding:
    api-key: ${EMBEDDING_API_KEY:}
    api-url: ${EMBEDDING_API_URL:https://api.siliconflow.cn/v1}
    model: ${EMBEDDING_MODEL:BAAI/bge-m3}
    dim: ${EMBEDDING_DIM:1024}
```

**自检**：`mvn -q -DskipTests compile` 通过；启动后端（`RAG_ENABLED` 不设，默认 false）无报错，`verify.cmd` 仍 27 全绿。

---

## 4. 任务清单总表（实现顺序即编号顺序）

| 任务 | 内容 | 主要落点 | 依赖 |
|---|---|---|---|
| T0 | 前置环境检查 | — | — |
| T1 | 2 个迁移 SQL + ApiLog 实体加字段 | `hello/migrations/`、`entity/ApiLog` | T0 |
| T2 | AppProperties + application.yml 配置层 | `config/AppProperties`、`application.yml` | T0 |
| T3 | RagVector 实体 + RagVectorMapper | `entity/`、`mapper/` | T1 |
| T4 | EmbeddingClient | `service/rag/EmbeddingClient` | T2 |
| T5 | VectorStore | `service/rag/VectorStore` | T3 |
| T6 | Retriever | `service/rag/Retriever` | T4、T5 |
| T7 | PromptComposer | `service/rag/PromptComposer` | 无（纯函数） |
| T8 | RagService 门面 + AsyncConfig | `service/rag/RagService`、`config/AsyncConfig` | T6、T7 |
| T9 | 接入 ChatService 两处（零破坏） | `service/ChatService` | T8 |
| T10 | A2：PromptTemplates.VERSION + api_log 埋点 | `util/PromptTemplates`、`ChatService` | T1 |
| T11 | A3：结构化输出 + 兜底 + 纠错重试 | `util/StructuredOutputParser`、`ChatService`、`PromptTemplates` | 无强依赖 |
| T12 | E1：eval/cases.json + eval/eval.mjs | `spring-backend/eval/` | T9、T10、T11 |
| T13 | CLI 工具：seed / backfill / demo | `tools/RagCli`、主类改造、`scripts/rag*.cmd` | T8 |

---

## 5. A1 RAG 详细设计（任务 T3–T9）

### 5.1 T3：实体与 Mapper

**`entity/RagVector.java`** [NEW]

```java
package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** rag_vector：RAG 向量存储（批次1/A1，对应迁移 create_rag_vector.sql） */
@Data
@TableName("rag_vector")
public class RagVector {
    @TableId(value = "vector_id", type = IdType.AUTO)
    private Long vectorId;
    private String sourceType;      // history | template
    private Integer userId;
    private Integer refId;
    private String title;
    private String embedText;
    private String content;
    private String embedding;       // JSON 数组文本
    private Integer dim;
    private String model;
    private LocalDateTime createdAt;
}
```

**`mapper/RagVectorMapper.java`** [NEW]（纯 BaseMapper，对齐 `ApiLogMapper` 风格）

```java
package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.entity.RagVector;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagVectorMapper extends BaseMapper<RagVector> {
}
```

### 5.2 T4：EmbeddingClient

**`service/rag/RagUnavailableException.java`** [NEW]

```java
package com.pg.pgfplots.service.rag;

/** RAG 依赖不可用的受控异常：只在 service/rag 包内部传递，由 RagService 统一吞掉降级，绝不外抛 */
public class RagUnavailableException extends RuntimeException {
    public RagUnavailableException(String message) { super(message); }
    public RagUnavailableException(String message, Throwable cause) { super(message, cause); }
}
```

**`service/rag/EmbeddingClient.java`** [NEW]（HTTP 风格对齐 `client/LlmClient`：Spring 6 `RestClient` + JDK HttpClient）

```java
package com.pg.pgfplots.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [RAG] embedding 客户端：封装 OpenAI 兼容 POST {api-url}/embeddings。
 * 超时取 app.rag.timeout-ms（独立于 LLM 的 45s）；响应后做数量与 dim 一致性校验，
 * 一切失败抛 RagUnavailableException（由 RagService 吞掉降级）。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties.Embedding embedding;
    private final AppProperties.Rag rag;

    public EmbeddingClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.embedding = appProperties.getEmbedding();
        this.rag = appProperties.getRag();
        this.objectMapper = objectMapper;
        // 风格对齐 LlmClient：JDK HttpClient + 超时
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(rag.getTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(rag.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(embedding.getApiUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + embedding.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 单条文本向量化 */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /** 批量向量化；校验返回条数与 dim 一致 */
    public List<float[]> embedBatch(List<String> texts) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", embedding.getModel());
            body.put("input", texts);
            String resp = restClient.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new RagUnavailableException("[RAG] embeddings 返回条数不符：期望 " + texts.size() + "，实际 " + data.size());
            }
            List<float[]> result = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                if (!arr.isArray() || arr.size() != embedding.getDim()) {
                    throw new RagUnavailableException("[RAG] embedding 维度不符：配置 dim=" + embedding.getDim()
                            + "，实际 " + (arr.isArray() ? arr.size() : "缺失"));
                }
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
                result.add(vec);
            }
            return result;
        } catch (RagUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new RagUnavailableException("[RAG] embedding 调用失败：" + e.getMessage(), e);
        }
    }
}
```

> 实现备注：`LlmClient` 用了异常转换（`HttpStatusCodeException`→`LlmApiException` 等），此处统一简化为 `RagUnavailableException`——因为 RAG 只有一种出口（静默降级），不需要细分。

### 5.3 T5：VectorStore

**`service/rag/VectorStore.java`** [NEW]

```java
package com.pg.pgfplots.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pg.pgfplots.entity.RagVector;
import com.pg.pgfplots.mapper.RagVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [RAG] rag_vector 读写：幂等 upsert = 先按 (source_type, ref_id) 删除再插入。
 * 查询只做两种：按 user_id 隔离加载 history、全量加载 template。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStore {

    private final RagVectorMapper mapper;
    private final ObjectMapper objectMapper;

    /** 写入/覆盖一条历史案例向量（仅生成成功的记录会进来） */
    public void upsertHistory(Integer userId, Integer refId, String embedText, String content, float[] vec, String model, int dim) {
        deleteByRef("history", refId);
        insert("history", userId, refId, null, embedText, content, vec, model, dim);
    }

    /** 批量重建模板库（seed 专用）：先清空 template 分区再全量写入，模板 refId = 1..N 固定顺序 */
    public void rebuildTemplates(List<RagVector> templates) {
        mapper.delete(new LambdaQueryWrapper<RagVector>().eq(RagVector::getSourceType, "template"));
        // MyBatis-Plus 3.5.5 BaseMapper 无批量 insert，逐条即可（模板总数 < 50）
        for (RagVector row : templates) {
            mapper.insert(row);
        }
    }

    /** 加载当前用户的历史向量（数量上限 app.rag.history-limit） */
    public List<RagVector> loadHistory(Integer userId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RagVector>()
                .eq(RagVector::getSourceType, "history")
                .eq(RagVector::getUserId, userId)
                .last("LIMIT " + limit));
    }

    /** 加载模板向量（数量上限 app.rag.template-limit） */
    public List<RagVector> loadTemplates(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RagVector>()
                .eq(RagVector::getSourceType, "template")
                .last("LIMIT " + limit));
    }

    private void deleteByRef(String sourceType, Integer refId) {
        mapper.delete(new LambdaQueryWrapper<RagVector>()
                .eq(RagVector::getSourceType, sourceType)
                .eq(RagVector::getRefId, refId));
    }

    private void insert(String sourceType, Integer userId, Integer refId, String title,
                        String embedText, String content, float[] vec, String model, int dim) {
        RagVector row = new RagVector();
        row.setSourceType(sourceType);
        row.setUserId(userId);
        row.setRefId(refId);
        row.setTitle(title);
        row.setEmbedText(embedText);
        row.setContent(content);
        row.setEmbedding(toJson(vec));
        row.setDim(dim);
        row.setModel(model);
        mapper.insert(row);
    }

    /** float[] → JSON 数组文本 */
    public String toJson(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 9).append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
```

> 实现备注：`rebuildTemplates` 里若逐条 insert 千级以内完全够用（模板总数 < 50 条）；`toJson` 手拼比 `ObjectMapper.writeValueAsString(float[])` 快且省依赖，二选一皆可，保留注释。

### 5.4 T6：Retriever

**`service/rag/Retriever.java`** [NEW]

```java
package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.entity.RagVector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [RAG] 检索器：全量加载 → 暴力余弦 → 阈值过滤 → 分区 Top-k。
 * 数据量千级 × 1024 维 ≈ 百万级浮点乘加，毫秒级，无需 ANN/向量库（演进路径：数据上万换 VectorStore 实现）。
 */
@Component
@RequiredArgsConstructor
public class Retriever {

    private final VectorStore vectorStore;
    private final AppProperties appProperties;

    /** 单条召回结果 */
    public record Retrieved(String sourceType, Integer refId, String title, String content, double score) {}

    /**
     * 检索入口：历史与模板分区独立 Top-k（互不挤占名额）。
     * @param queryVec 查询向量（由 RagService 调 EmbeddingClient 得到）
     */
    public List<Retrieved> retrieve(Integer userId, float[] queryVec) {
        AppProperties.Rag rag = appProperties.getRag();
        List<Retrieved> hits = new ArrayList<>();
        hits.addAll(topK(vectorStore.loadHistory(userId, rag.getHistoryLimit()), queryVec, rag.getMinScore(), rag.getTopKHistory()));
        hits.addAll(topK(vectorStore.loadTemplates(rag.getTemplateLimit()), queryVec, rag.getMinScore(), rag.getTopKTemplate()));
        return hits;
    }

    private List<Retrieved> topK(List<RagVector> candidates, float[] queryVec, double minScore, int k) {
        List<Retrieved> scored = new ArrayList<>(candidates.size());
        for (RagVector row : candidates) {
            float[] vec = parse(row.getEmbedding());
            if (vec == null || vec.length != queryVec.length) continue; // dim 不符直接跳过（换模型后的旧行）
            double score = cosine(queryVec, vec);
            if (score >= minScore) {
                scored.add(new Retrieved(row.getSourceType(), row.getRefId(), row.getTitle(), row.getContent(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Retrieved::score).reversed());
        return scored.subList(0, Math.min(k, scored.size()));
    }

    /** JSON 数组文本 → float[]；解析失败返回 null（该行视为脏数据跳过） */
    private float[] parse(String json) {
        try {
            String[] parts = json.substring(1, json.length() - 1).split(",");
            float[] vec = new float[parts.length];
            for (int i = 0; i < parts.length; i++) vec[i] = Float.parseFloat(parts[i].trim());
            return vec;
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度（暴力双循环，已归一化与否均成立） */
    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
```

### 5.5 T7：PromptComposer

**`service/rag/PromptComposer.java`** [NEW]（纯函数，可写 static，风格对齐 `ChartCodeExtractor`）

```java
package com.pg.pgfplots.service.rag;

import java.util.List;

/**
 * [RAG] few-shot 段落组装。
 * 关键措辞约束：显式声明「仅参考图型写法，禁止照搬数据」，并说明与 SYSTEM_PROMPT
 * 中【上下文独立指令】不冲突（示例不属于对话历史，不会污染上下文独立语义）。
 */
public final class PromptComposer {

    private PromptComposer() {}

    public static String compose(List<Retriever.Retrieved> hits) {
        if (hits == null || hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n【参考示例（仅供图型写法参考）】");
        sb.append("\n以下是从你过往成功案例与图型模板库中检索到的相似需求示例。注意：这些示例与【上下文独立指令】不冲突——");
        sb.append("它们不是本次对话历史，仅用于参考其 PGFPlots 图型选择与语法写法；禁止照搬示例中的任何数据、数值、坐标与标签，");
        sb.append("当前结果仍只依据本次用户消息与数据文件。");
        int i = 1;
        for (Retriever.Retrieved hit : hits) {
            sb.append("\n\n--- 示例").append(i++).append("（").append(hit.title()).append("，相似度 ")
              .append(String.format("%.2f", hit.score())).append("）---\n");
            sb.append(hit.content());
        }
        return sb.toString();
    }
}
```

### 5.6 T8：RagService 门面 + 异步配置

**`config/AsyncConfig.java`** [NEW]（全工程目前无 @EnableAsync，本次为 RAG 异步索引引入，仅此一处）

```java
package com.pg.pgfplots.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 批次1/A1：RAG 异步索引线程池。队列满直接丢弃并记日志（索引是纯增强，丢了不影响主流程，下轮生成会重建） */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("ragIndexExecutor")
    public ThreadPoolTaskExecutor ragIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-index-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((r, e) ->
                org.slf4j.LoggerFactory.getLogger("RagService").warn("[RAG] 索引队列已满，丢弃一条索引任务"));
        executor.initialize();
        return executor;
    }
}
```

**`service/rag/RagService.java`** [NEW]（唯一对外门面：ChatService 只依赖这一个类）

```java
package com.pg.pgfplots.service.rag;

import com.pg.pgfplots.config.AppProperties;
import com.pg.pgfplots.util.SystemLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [RAG] 检索增强门面（批次1/A1）。ChatService 唯一依赖点。
 * 降级规则：enabled=false → 立即返回 ""/忽略；embedding 未配置 / 超时 / 报错 / 维度不符 →
 * systemLogWriter.warning + log.warn，返回 ""（主流程零影响）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final AppProperties appProperties;
    private final EmbeddingClient embeddingClient;
    private final Retriever retriever;
    private final VectorStore vectorStore;
    private final SystemLogWriter systemLogWriter;

    /**
     * 生成前召回 few-shot 段落。任何失败返回 ""，调用方直接跳过拼接。
     * @param userId 当前用户（历史隔离）；@param query 用户原始消息
     */
    public String retrieveFewShot(Integer userId, String query) {
        if (!appProperties.getRag().isEnabled() || query == null || query.isBlank()) return "";
        if (isBlank(appProperties.getEmbedding().getApiKey())) return ""; // 未配置即降级
        try {
            float[] queryVec = embeddingClient.embed(query);
            List<Retriever.Retrieved> hits = retriever.retrieve(userId, queryVec);
            String fewShot = PromptComposer.compose(hits);
            log.info("[RAG] 召回 {} 条（user={}）", hits.size(), userId);
            return fewShot;
        } catch (Exception e) {
            log.warn("[RAG] 召回失败，静默降级：{}", e.getMessage());
            systemLogWriter.warning("[RAG] 召回失败，已降级：" + e.getMessage());
            return "";
        }
    }

    /**
     * 生成成功后异步索引历史案例（fire-and-forget：异常全吞，绝不上抛）。
     * 只索引 chartCode 非空的成功生成；embed_text = 用户需求描述，content = 图型摘要 + tikz 代码。
     */
    @Async("ragIndexExecutor")
    public void indexHistoryAsync(Integer userId, Integer historyId, String description, String chartCode) {
        try {
            if (!appProperties.getRag().isEnabled() || historyId == null || chartCode == null || chartCode.isBlank()) return;
            if (isBlank(appProperties.getEmbedding().getApiKey())) return;
            String embedText = description == null || description.isBlank() ? "AI图表生成" : description;
            String content = "需求：" + embedText + "\n代码：\n" + chartCode;
            float[] vec = embeddingClient.embed(embedText);
            vectorStore.upsertHistory(userId, historyId, embedText, content, vec,
                    appProperties.getEmbedding().getModel(), appProperties.getEmbedding().getDim());
            log.info("[RAG] 历史案例已索引 historyId={} user={}", historyId, userId);
        } catch (Exception e) {
            log.warn("[RAG] 索引失败（忽略）：historyId={}，{}", historyId, e.getMessage());
            systemLogWriter.warning("[RAG] 索引失败 historyId=" + historyId + "：" + e.getMessage());
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
```

> 实现备注：`indexHistoryAsync` 上方无需额外接口；`@Async("ragIndexExecutor")` 由 `AsyncConfig` 的 `@EnableAsync` 激活，且 RagService 是独立 Bean，不存在自调用失效问题。

### 5.7 T9：接入 ChatService（全工程仅两处改动，零破坏）

**`service/ChatService.java`** [MODIFY]

改动 1：新增依赖注入字段（加在现有 `private final` 字段区末尾）：

```java
private final RagService ragService;
```

改动 2：`buildSystemPrompt`（现 L142-179）——在 `new StringBuilder(PromptTemplates.SYSTEM_PROMPT)` 处改为：

```java
// [RAG] 召回 few-shot：关闭或失败时返回 ""，拼接结果与旧版逐字符一致（零破坏）
String fewShot = ragService.retrieveFewShot(userId, userMessage);
StringBuilder sb = new StringBuilder(PromptTemplates.SYSTEM_PROMPT + fewShot);
```

（其余逻辑不动：few-shot 拼在 SYSTEM_PROMPT 之后、数据集段之前。）

改动 3：`saveGenerationHistory`（现 L215-295）——在 api_log 插入成功后（现 L287 之后、return 之前）追加：

```java
// [RAG] 异步索引历史成功案例（fire-and-forget，内部全吞异常）
if (history.getHistoryId() != null && chartCode != null && !chartCode.isBlank()) {
    ragService.indexHistoryAsync(userId, history.getHistoryId(),
            history.getGenerationDescription(), chartCode);
}
```

**自检（T9 完成后）**：
1. `RAG_ENABLED` 不设 → 抓 `buildSystemPrompt` 返回值（可临时 log.debug）与改造前**逐字符一致**；跑 `verify.cmd` 27 全绿；
2. 设 `RAG_ENABLED=true` + `EMBEDDING_API_KEY=<无效值>` → 生成请求正常返回（embedding 报错只出现在 system_log），验证静默降级。

### 5.8 T13：CLI 工具（seed_templates / backfill_history / rag_demo）

主类改造 **`PgfplotsBackendApplication.java`** [MODIFY]：

```java
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(PgfplotsBackendApplication.class);
    // [RAG] CLI 模式（seed/backfill/demo）以非 Web 方式运行，执行完退出
    if (Arrays.stream(args).anyMatch(a -> a.startsWith("--rag-cli="))) {
        app.setWebApplicationType(WebApplicationType.NONE);
    }
    app.run(args);
}
```

**`tools/RagCli.java`** [NEW]（实现 `CommandLineRunner`，非 `--rag-cli` 启动时 no-op）：

```java
package com.pg.pgfplots.tools;

/** [RAG] CLI：java -jar ... --rag-cli=seed | backfill | demo:<自然语言查询>，详见 scripts/rag*.cmd */
@Component
@RequiredArgsConstructor
public class RagCli implements CommandLineRunner {

    private final RagService ragService;
    private final EmbeddingClient embeddingClient;
    private final Retriever retriever;
    private final VectorStore vectorStore;
    private final GenerationHistoryMapper historyMapper;
    private final AppProperties appProperties;
    private final SystemLogWriter systemLogWriter;

    @Override
    public void run(String... args) {
        String mode = Arrays.stream(args)
                .filter(a -> a.startsWith("--rag-cli="))
                .findFirst().map(a -> a.substring("--rag-cli=".length())).orElse(null);
        if (mode == null) return; // 正常启动，no-op
        try {
            if (mode.equals("seed")) seed();
            else if (mode.equals("backfill")) backfill();
            else if (mode.startsWith("demo:")) demo(mode.substring("demo:".length()));
            else System.out.println("未知模式：" + mode + "（支持 seed / backfill / demo:<query>）");
        } finally {
            System.exit(0); // CLI 执行完立即退出，不进入 Web 服务
        }
    }
    // seed()/backfill()/demo() 三个私有方法见 5.8.1~5.8.3
}
```

#### 5.8.1 `seed()`：重建模板库

- 模板数据**直接取自 `util/PromptTemplates` 的【进阶图型模板】段落**（无需新建内容源），在 `RagCli` 内以 `record TemplateSeed(int refId, String title, String embedText, String content)` 列表硬编码 **7 条**：柱状图 / 折线图 / 饼图 / 散点图 / 堆叠柱状图 / 密集柱状图（ybar 缩写）/ 误差棒。每条 `content` 为该图型的 tikz 片段（从 SYSTEM_PROMPT 对应段落摘出）、`embedText` 为「标题 + 图型关键词」一句话（如「柱状图：ybar 分组对比、图例外置、坐标轴标注」）。
- 流程：逐条 `embeddingClient.embed(embedText)` → 组装 `RagVector`（`source_type='template'`，`ref_id` 用 1..7，`user_id=null`）→ `vectorStore.rebuildTemplates(...)` 全量重建 → 打印条数。

#### 5.8.2 `backfill()`：回填历史成功案例

- 查询全部 `generation_history` 中 `generation_code` 非空的行（`LambdaQueryWrapper.isNotNull/ ne("")`，`last("LIMIT 2000")`）；
- **分批（每批 20 条）+ 批间 sleep 200ms + 打印进度 `已处理 x/y`**；逐条 embed → `upsertHistory(userId, historyId, generationDescription, content=“需求：...\n代码：...”)`；
- 单条失败 log.warn 跳过继续，结束打印成功/失败计数。

#### 5.8.3 `demo(query)`：召回演示（面试用）

- 入参解析：`--rag-cli=demo:<查询文本>`，可选第三段 `:<userId>`（即 `--rag-cli=demo:<查询文本>:<userId>`，缺省 userId=1）；
- 流程：`embeddingClient.embed(query)` → 复用 `Retriever.retrieve(userId, queryVec)`（无需新增重载，历史按该 userId 加载 + 模板全量加载，正好满足演示「模板 + 指定用户历史」）；
- 打印 Top-k：`[template#3] 密集柱状图（相似度 0.87）`，逐行输出，展示 source_type / ref_id / title / score。

**`scripts/` 下 3 个 cmd 包装** [NEW]（对齐现有 `verify.cmd` 风格，先 build 再跑）：

```cmd
@echo off
rem scripts\rag_seed.cmd —— 重建模板库
cd /d %~dp0..
call scripts\build.cmd
java -jar target/pgfplots-backend-1.0.0.jar --rag-cli=seed
```

同款：`rag_backfill.cmd`（`--rag-cli=backfill`）、`rag_demo.cmd`（`--rag-cli=demo:%*`，把命令行参数当查询传入）。

**自检（T13 完成后）**：
1. `scripts\rag_seed.cmd` 输出 `模板库已重建，共 7 条`；`SELECT COUNT(*) FROM rag_vector WHERE source_type='template';` = 7；
2. 重复执行 seed，行数仍为 7（幂等）；
3. `scripts\rag_backfill.cmd` 有历史时逐条索引成功；
4. `scripts\rag_demo.cmd "画一个各季度销售额对比的柱状图"` 输出带相似度得分的 Top-k。

---

## 6. A2 Prompt 工程化：版本号埋点（任务 T10）

1. **`util/PromptTemplates.java`** [MODIFY]：类顶部新增：

```java
/** 提示词版本号（批次1/A2）：SYSTEM_PROMPT 或注入策略变更时递增，并写入 api_log.prompt_version 便于对比 */
public static final String VERSION = "v1.1-rag";
```

2. **`entity/ApiLog.java`** [MODIFY]：新增字段 `private String promptVersion;`（T1 已加列）。
3. **`service/ChatService.java`** [MODIFY] 两处埋点：
   - `saveGenerationHistory` 中构造 `ApiLog` 处（现 L281-287）加 `apiLog.setPromptVersion(PromptTemplates.VERSION);`
   - `recordFailedCall`（现 L337-351）加 `log.setPromptVersion(PromptTemplates.VERSION);`

**自检**：成功与失败各触发一次生成，`SELECT prompt_version FROM api_log ORDER BY call_id DESC LIMIT 2;` 均为 `v1.1-rag`；`verify.cmd` 27 全绿。

---

## 7. A3 结构化输出：JSON 优先 + 正则兜底 + 纠错重试一次（任务 T11）

### 7.1 `util/StructuredOutputParser.java` [NEW]

```java
package com.pg.pgfplots.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [A3] 结构化输出解析：优先解析模型返回的 JSON {"chart_type","code","summary"}；
 * 任何失败返回 null，由调用方回退 ChartCodeExtractor 正则兜底——绝不因模型不听话而整体失败。
 */
public final class StructuredOutputParser {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern BARE_JSON = Pattern.compile("\\{[\\s\\S]*\\}");

    private StructuredOutputParser() {}

    /** 解析结果：code 必须非空才视为有效 */
    public record Structured(String chartType, String code, String summary) {}

    public static Structured parse(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) return null;
        String json = null;
        Matcher fenced = JSON_FENCE.matcher(aiReply);
        if (fenced.find()) json = fenced.group(1);
        else {
            Matcher bare = BARE_JSON.matcher(aiReply);
            if (bare.find()) json = bare.group();
        }
        if (json == null) return null;
        try {
            JsonNode root = OM.readTree(json);
            String code = root.path("code").asText("").trim();
            if (code.isEmpty() || !code.contains("tikzpicture")) return null; // 结构不符 → 走兜底
            return new Structured(root.path("chart_type").asText(""), code, root.path("summary").asText(""));
        } catch (Exception e) {
            return null;
        }
    }
}
```

### 7.2 提示词追加

**`util/PromptTemplates.java`** [MODIFY] 新增常量（不改 SYSTEM_PROMPT 原文，由 `buildSystemPrompt` 拼接，便于回滚）：

```java
/** [A3] 结构化输出约定：要求模型在围栏代码块之外，追加输出一个 JSON 对象 */
public static final String STRUCTURED_OUTPUT_SUFFIX =
        "\n\n【结构化输出约定】在 ```latex 代码块之外，请另输出一个 JSON 对象："
        + "{\"chart_type\": \"<图型英文标识，如 bar/line/pie/scatter>\", "
        + "\"code\": \"<与围栏内完全一致的 tikzpicture 代码>\", "
        + "\"summary\": \"<一句话中文摘要，20 字以内>\"}。"
        + "JSON 必须放在 ```json 代码块中，且 code 字段与 latex 代码块内容一致。";
```

**`service/ChatService.buildSystemPrompt`** [MODIFY]：在 SYSTEM_PROMPT 拼接处追加（注意 fewShot 之后、数据集段之前）：

```java
StringBuilder sb = new StringBuilder(PromptTemplates.SYSTEM_PROMPT + fewShot + PromptTemplates.STRUCTURED_OUTPUT_SUFFIX);
```

### 7.3 解析与兜底链（`ChatService.generate` [MODIFY]，现 L118-121 处）

```java
// [A3] 解析顺序：JSON 结构化 → 正则兜底（ChartCodeExtractor），绝不因模型不听话而失败
StructuredOutputParser.Structured structured = StructuredOutputParser.parse(aiReply);
String chartCode;
if (structured != null) {
    chartCode = structured.code();
} else if (request.getChartCode() != null && !request.getChartCode().isBlank()) {
    chartCode = request.getChartCode();                 // 前端显式传入优先
} else {
    chartCode = ChartCodeExtractor.extract(aiReply);    // 正则兜底
}
```

### 7.4 纠错重试一次（`generateWithFallback` [MODIFY]，现 L181-209）

在首次拿到非空 `content` 后（现 L186 之后、空回复判断之前）插入：

```java
// [A3] 结构化校验失败（JSON 无效且正则提不出代码）→ 带错误信息重试一次（同模型）
if (ChartCodeExtractor.extract(reply).isEmpty() && StructuredOutputParser.parse(reply) == null) {
    String corrective = systemPrompt + "\n\n【上次输出无效】你上一次的回复既没有可解析的 JSON，也没有 ```latex 围栏代码。"
            + "请严格按【结构化输出约定】重新输出。";
    LlmResponse retry = callOnce(providerOf(requestedModel), corrective, userMessage, disableThinkingOf(requestedModel));
    if (retry != null && retry.content() != null && !retry.content().isBlank()) {
        reply = retry.content();
    }
}
```

### 7.5 响应体扩展（`generate` 返回的 `LinkedHashMap`，现 L132-139）

追加两个可选键（structured 为 null 时不放入，保持旧客户端兼容）：

```java
if (structured != null) {
    result.put("chart_type", structured.chartType());
    result.put("summary", structured.summary());
}
```

**自检（T11 完成后）**：
1. 正常请求返回体含 `chart_type` / `summary`；
2. **构造散文用例**：临时把 `STRUCTURED_OUTPUT_SUFFIX` 移除并发一个会被模型自由发挥的消息 → 确认正则兜底仍能出码（验证兜底链）；测完恢复；
3. `verify.cmd` 27 全绿。

---

## 8. E1 离线评估集（任务 T12）

### 8.1 `spring-backend/eval/cases.json` [NEW]

固定 10 条无数据集用例（`自行生成`路线，可控、可复现、不依赖 fixture 上传）。首次创建建议：

```json
[
  { "id": "bar_quarter",   "message": "画一个 2023 年四个季度销售额对比柱状图，图例外置" },
  { "id": "line_trend",    "message": "画一个 2018-2024 年新能源汽车销量的折线图，标注数据点" },
  { "id": "pie_share",     "message": "画一个全球智能手机市场份额饼图，图例放右侧" },
  { "id": "scatter",       "message": "画一个身高体重关系的散点图，带图例" },
  { "id": "bar_stacked",   "message": "画一个 2020-2024 年线上/线下渠道销售额堆叠柱状图" },
  { "id": "bar_dense",     "message": "画一个 12 个月降水量的密集柱状图（ybar 紧凑写法）" },
  { "id": "error_bar",     "message": "画一个带误差棒的实验数据对比柱状图" },
  { "id": "line_multi",    "message": "画一个双系列折线图（产量与销量），标注错开避免重叠" },
  { "id": "bar_negative",  "message": "画一个含正负值的月度盈亏柱状图" },
  { "id": "bar_ambiguity", "message": "画一个十年人口增长趋势图" }
]
```

> 最后一条故意模糊（未指明图型），用于观察 RAG 召回对图型选择的引导作用。

### 8.2 `spring-backend/eval/eval.mjs` [NEW]

零依赖 Node 脚本（复用 `verify.js` 风格：全局 `fetch` + `AbortSignal.timeout`，仅内置模块）：

- **登录**：照抄 `verify.js` 中的管理员登录函数（用户名/密码与其一致：admin123 / 666666，具体端点以 verify.js 为准），拿 token。
- **参数**：`node eval/eval.mjs --tag=baseline`（tag 默认 `run`，`--base=http://localhost:3000` 可覆盖，默认同 `VERIFY_BASE`；`--no-start` 语义同 verify.js——可选实现，最小版要求后端已启动）。
- **逐用例流程**：
  1. `POST /api/chat`（body `{message, model:"deepseek"}`，超时 120s），记录 `history_id` 与耗时；
  2. 若响应 `success` 且 `chart_code` 非空 → `POST /api/compile/{history_id}`（超时 60s），编译成功记 `compiled=true`；
  3. **首轮通过率**定义：首轮生成（不发生降级/重试）的 chart_code 直接编译成功——脚本以「chat success && chart_code 非空 && compile success」计为通过（近似口径，够用且可解释）。
- **指标汇总**：生成成功率 = chat success 数 / 总数；可编译率 = compile success / 生成成功数；首轮通过率 = 通过数 / 总数；平均/ P95 耗时（chat 阶段）。
- **产出**：控制台打印汇总表；写 `spring-backend/eval/results/<tag>.json`：

```json
{ "tag": "baseline", "run_at": "2026-09-13T10:00:00+08:00", "cases": [ ... 每条含 id/ok/latency_ms/history_id/compile_ok ... ],
  "metrics": { "total": 10, "gen_success_rate": 0.9, "compile_rate": 0.89, "first_pass_rate": 0.8, "avg_latency_ms": 21000, "p95_latency_ms": 34000 } }
```

**运行方式**（注意 cmd 中 `set` 与命令必须分行写，写在同一行会把尾部空格带进变量值）：

```cmd
:: 基线（RAG 关）
set RAG_ENABLED=false
mvn package
start java -jar target/pgfplots-backend-1.0.0.jar
node eval/eval.mjs --tag=rag-off

:: 开 RAG（先 rag_seed + rag_backfill，再重启后端）
set RAG_ENABLED=true
mvn package
start java -jar target/pgfplots-backend-1.0.0.jar
node eval/eval.mjs --tag=rag-on

:: 对比两个 results/*.json 的 first_pass_rate —— 这就是要写进简历的数字
```

**自检**：跑一次 `--tag=smoke`，10 条全部产生记录、results JSON 生成、指标口径正确（可用手数校验）。

---

## 9. 总验收清单（对齐 plan-AI.md §11，全部通过才算批次1完成）

> **验收执行记录（2026-09-13，硅基流动 EMBEDDING_API_KEY 实测）**：
> ✅1 基线 27 绿（改造前/T9 后/终验共 3 轮）｜✅2 RAG off 行为一致（构造保证+回归）｜✅3 few-shot 注入（`[RAG] 召回 3 条（user=1）` 日志 + 活体生成 historyId=219）｜✅4 静默降级（密钥缺失 401 → 清晰报错即退；RagService catch-all 降级）｜✅5 无关查询 0 召回（0.43 < 阈值 0.55；相关查询 0.71/0.56）｜✅6 数据隔离（userId=2 无历史 → 仅模板分区；历史按 user_id=1/4/11/15 分布）｜✅7 幂等索引（重复 backfill 后 188 行 = 181+7，(source_type,ref_id) 全唯一）｜✅8 结构化输出（活体返回 chart_type=bar + summary；8 项单测）｜✅10 prompt_version=v1.1-rag 落库（call_id=272，历史行为 NULL 属预期）｜✅11 E1 对比数据（`eval/cases.json` 10 条：rag-off 首轮通过率 1.0 / rag-on 1.0，各结果见 `eval/results/rag-off.json` 与 `rag-on.json`）。
> 调参记录：min-score 默认值从 0.72 调至 0.55（`${RAG_MIN_SCORE:0.55}`，实测 bge-m3 相关对 0.5–0.75 区间），模板 embedText 改为自然句式提升召回，历史行 title 取需求描述截断 50 字。

| # | 验收项 | 方法 | 通过标准 |
|---|---|---|---|
| 1 | 基线不回归 | `scripts\verify.cmd` | PASS=27 / FAIL=0 |
| 2 | RAG off 行为一致 | 不设 `RAG_ENABLED`，抓 `buildSystemPrompt` 产出 | 与改造前逐字符一致 |
| 3 | few-shot 注入可见 | `RAG_ENABLED=true` + 有效 embedding key + 已 seed/backfill，发一条与模板/历史相似的需求 | prompt 日志可见【参考示例】段；`[RAG] 召回 N 条` 日志出现 |
| 4 | 静默降级 | `EMBEDDING_API_KEY` 设为无效值再发请求 | 生成正常返回；system_log 有 `[RAG]` warning；无任何 5xx |
| 5 | 空/低分召回回落 | 发一条与模板库完全不相关的需求 | 召回 0 条时 prompt 与 RAG off 一致（PromptComposer 返回 ""） |
| 6 | 数据隔离 | 用户 A 的历史不出现在用户 B 的召回中 | `Retriever` 的 history 查询带 `eq(userId)`（代码审查 + 双账号实测） |
| 7 | 幂等索引 | 同一 historyId 生成两次 | `rag_vector` 中该 ref_id 只有一行 |
| 8 | 结构化输出 | 正常请求 | 返回体含 `chart_type`/`summary`；code 与围栏一致 |
| 9 | A3 兜底 | 模型返回散文（无 JSON 无围栏） | 正则兜底出码或触发一次纠错重试，接口不失败 |
| 10 | A2 版本埋点 | 查最近 api_log | `prompt_version` = `v1.1-rag`（成功与失败记录都有） |
| 11 | E1 产出对比数据 | `rag-off` 与 `rag-on` 各跑一次 | 两份 results JSON，`first_pass_rate` 可对比 |

---

## 10. 回归说明（执行节奏）

1. **每个任务（T1–T13）完成后**：`mvn -q -DskipTests compile` 保证可编译。
2. **每个大节完成后（T9 后 / T11 后 / T12 后）**：`scripts\build.cmd && scripts\verify.cmd`，必须 27 全绿才允许进入下一节。
3. **全部完成后**：按 §8.2 流程产出 `rag-off` / `rag-on` 两份评估数据，随后可回填 `plan-AI.md` §12 面试叙事中的 X/Y 数字。
4. 完成后在 `plan-AI.md` §4 批次表给「批次 1」标注完成状态，并把本文件标记为已执行。

---

## 附：本文件与 plan-AI.md 的映射

| 本文件 | plan-AI.md |
|---|---|
| §2.1 表结构 / §5.1–5.7 | §5.3 落地设计 + §5.1 方案② |
| §5.8 CLI 与脚本 | §5.4 配套脚本 |
| §6 | §6 A2 |
| §7 | §6 A3 |
| §8 | §8 E1 |
| §9 | §11 验收 |
| 约束 1（不上框架） | §5.1 方案③否决 + §10 |
