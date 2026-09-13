package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 生成历史表 generation_history */
@Data
@TableName("generation_history")
public class GenerationHistory {

    @TableId(value = "history_id", type = IdType.AUTO)
    private Integer historyId;

    private Integer userId;

    /** 关联数据集（可空） */
    private Integer dataId;

    /** 生成描述（用户输入） */
    private String generationDescription;

    /** 生成的 LaTeX 代码 */
    private String generationCode;

    /** 编译产物相对路径 */
    private String generationPath;

    private LocalDateTime generationTime;
}
