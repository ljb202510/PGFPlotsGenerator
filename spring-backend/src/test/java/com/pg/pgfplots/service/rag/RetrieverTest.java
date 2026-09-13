package com.pg.pgfplots.service.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 批次1/A1 自检：Retriever 余弦相似度的确定性验证（同包可访问包级私有方法） */
class RetrieverTest {

    @Test
    void cosineIdenticalVectors() {
        float[] a = {1f, 2f, 3f};
        assertEquals(1.0, Retriever.cosine(a, a), 1e-9);
    }

    @Test
    void cosineOrthogonalVectors() {
        assertEquals(0.0, Retriever.cosine(new float[]{1f, 0f}, new float[]{0f, 1f}), 1e-9);
    }

    @Test
    void cosineZeroVectorIsSafe() {
        assertEquals(0.0, Retriever.cosine(new float[]{0f, 0f}, new float[]{1f, 1f}), 1e-9);
        assertFalse(Double.isNaN(Retriever.cosine(new float[]{0f, 0f}, new float[]{1f, 1f})));
    }
}
