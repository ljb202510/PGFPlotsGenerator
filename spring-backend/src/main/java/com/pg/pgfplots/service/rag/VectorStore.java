package com.pg.pgfplots.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pg.pgfplots.entity.RagVector;
import com.pg.pgfplots.mapper.RagVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [RAG] rag_vector 读写：幂等 upsert = 先按 (source_type, ref_id) 删除再插入。
 * <p>查询只做两种：按 user_id 隔离加载 history、全量加载 template。
 * 数据隔离约束：history 严格 eq(userId)；template 全局共享（user_id IS NULL）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStore {

    private final RagVectorMapper mapper;

    /** 写入/覆盖一条历史案例向量（仅生成成功的记录会进来） */
    public void upsertHistory(Integer userId, Integer refId, String title, String embedText, String content,
                              float[] vec, String model, int dim) {
        deleteByRef("history", refId);
        insert("history", userId, refId, title, embedText, content, vec, model, dim);
    }

    /** 批量重建模板库（seed 专用）：先清空 template 分区再全量写入；模板 refId 由 seed 固定为 1..N */
    public void rebuildTemplates(List<RagVector> templates) {
        mapper.delete(new LambdaQueryWrapper<RagVector>().eq(RagVector::getSourceType, "template"));
        // MyBatis-Plus 3.5.5 BaseMapper 无批量 insert，逐条即可（模板总数 < 50）
        for (RagVector row : templates) {
            mapper.insert(row);
        }
    }

    /** 加载当前用户的历史向量（数量上限 app.rag.history-limit，防全表膨胀） */
    public List<RagVector> loadHistory(Integer userId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RagVector>()
                .eq(RagVector::getSourceType, "history")
                .eq(RagVector::getUserId, userId)
                .last("LIMIT " + limit));
    }

    /** 加载全部历史向量（[v1.2] 清理工具用：不按 user 过滤，仍受 limit 保护） */
    public List<RagVector> loadAllHistory(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<RagVector>()
                .eq(RagVector::getSourceType, "history")
                .last("LIMIT " + limit));
    }

    /** 删除一条历史案例向量（[v1.2] 清理不合格案例用） */
    public void deleteHistoryRef(Integer refId) {
        deleteByRef("history", refId);
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

    /** float[] → JSON 数组文本（手拼比 ObjectMapper 序列化更快更省内存） */
    public String toJson(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 9).append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
