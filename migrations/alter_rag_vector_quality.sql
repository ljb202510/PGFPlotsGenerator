-- [语料治理] rag_vector 新增「质量等级」与「数据来源」两列
--
-- 背景：原实现「生成成功即入库」，无任何质量判定——既不校验编译成功，也不校验语义正确，
--       导致无效/错误案例被当作 few-shot 范例反复召回（错误强化）。本迁移引入质量分级，
--       把「入库」与「可召回」解耦：任何案例先入库，只有被独立判据判定过的才参与召回。
--
-- quality 取值：
--   golden     —— 人工定义真值（模板分区；或经人工审阅确认的范例）
--   verified   —— 独立判据确认（编译成功 且 静态零违例），由 `--rag-cli=verify:<userId>` 离线定级
--   unverified —— 默认等级，不参与召回
--
-- data_source 取值（**只标「有无数据集」，不声称能识别「模型自拟示意数据」**）：
--   dataset    —— 该次生成附带上传数据集
--   no-dataset —— 未附带数据集；其中既可能是「使用公开统计数据」，也可能是「模型自拟示意数据」，
--                 两者**无法自动区分**，属已知盲区
--
-- 兼容性：默认值 unverified + 回填 template→golden，保证迁移后行为与现状一致
--         （历史分区在 Retriever 侧有「过滤后为空则回退」兜底，不会出现空召回）。
USE `X`;

ALTER TABLE rag_vector
    ADD COLUMN quality ENUM('golden','verified','unverified') NOT NULL DEFAULT 'unverified'
        COMMENT '[语料治理] 语料质量等级' AFTER model,
    ADD COLUMN data_source VARCHAR(32) NULL
        COMMENT '[语料治理] 数据来源：dataset/no-dataset（无法区分公开数据与自拟示意数据）' AFTER quality;

-- 模板分区是人工撰写的图型范例 → 人工定义真值
UPDATE rag_vector SET quality = 'golden' WHERE source_type = 'template';

-- 历史分区保持默认 unverified，等待 `--rag-cli=verify:<userId>` 离线定级提升为 verified
UPDATE rag_vector SET quality = 'unverified' WHERE source_type = 'history';

-- 校验：迁移后各等级分布
SELECT source_type, quality, COUNT(*) AS cnt FROM rag_vector GROUP BY source_type, quality ORDER BY source_type, quality;
