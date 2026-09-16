package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * rag_vector：RAG 向量存储表（批次1/A1，对应迁移 create_rag_vector.sql）。
 * MySQL 存 JSON 向量 + Java 侧暴力余弦检索，不引入向量库中间件。
 */
@Data
@TableName("rag_vector")
public class RagVector {

    @TableId(value = "vector_id", type = IdType.AUTO)
    private Long vectorId;

    /** history | template */
    private String sourceType;

    /** history 归属用户（严格隔离）；template 为 NULL */
    private Integer userId;

    /** history → generation_history.history_id；template → 模板编号(1..N) */
    private Integer refId;

    /** 展示标题 */
    private String title;

    /** 参与向量化的文本 */
    private String embedText;

    /** 召回后注入提示词的片段 */
    private String content;

    /** JSON 数组文本，如 [0.12,-0.33,...] */
    private String embedding;

    /** 向量维度（换模型后可识别需重建） */
    private Integer dim;

    /** embedding 模型名 */
    private String model;

    /**
     * [语料治理] 质量等级：golden（人工定义真值）| verified（编译成功且静态零违例）| unverified（默认，不召回）。
     * <p>由迁移 {@code migrations/alter_rag_vector_quality.sql} 引入；判定与门槛见 {@code service.rag.RagQuality}。</p>
     */
    private String quality;

    /**
     * [语料治理] 数据来源：dataset / no-dataset。
     * <p><b>只标「有无上传数据集」，不声称能识别「模型自拟示意数据」</b>——「使用公开统计数据」与
     * 「模型自拟示意数据」都不带数据集，两者无法自动区分，属已知盲区。</p>
     */
    private String dataSource;

    private LocalDateTime createdAt;
}
