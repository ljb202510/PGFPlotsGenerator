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

    private LocalDateTime createdAt;
}
