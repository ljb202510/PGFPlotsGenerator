-- 批次1/A1：RAG 向量存储表（MySQL 存 JSON 向量 + Java 暴力余弦，无向量库中间件）
USE `X`;

CREATE TABLE IF NOT EXISTS rag_vector (
  vector_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type ENUM('history','template') NOT NULL COMMENT 'history=用户历史成功案例；template=进阶图型模板库',
  user_id     INT NULL COMMENT 'history 归属用户（严格隔离）；template 为 NULL',
  ref_id      INT NOT NULL COMMENT 'history → generation_history.history_id；template → 模板编号(1..N，seed 脚本固定顺序)',
  title       VARCHAR(255) COMMENT '展示标题（面试演示用）',
  embed_text  MEDIUMTEXT NOT NULL COMMENT '参与向量化的文本',
  content     MEDIUMTEXT NOT NULL COMMENT '召回后注入提示词的片段（tikz 代码等）',
  embedding   LONGTEXT NOT NULL COMMENT 'JSON 数组文本，如 [0.12,-0.33,...]',
  dim         INT NOT NULL COMMENT '维度一致性校验（换模型后可识别需重建）',
  model       VARCHAR(64) NOT NULL COMMENT 'embedding 模型名（同上）',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_ref (source_type, ref_id),
  INDEX idx_user (user_id),
  INDEX idx_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
