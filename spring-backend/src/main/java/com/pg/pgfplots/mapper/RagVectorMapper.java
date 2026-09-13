package com.pg.pgfplots.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pg.pgfplots.entity.RagVector;
import org.apache.ibatis.annotations.Mapper;

/** rag_vector 表 Mapper（纯 BaseMapper，风格对齐 ApiLogMapper） */
@Mapper
public interface RagVectorMapper extends BaseMapper<RagVector> {
}
