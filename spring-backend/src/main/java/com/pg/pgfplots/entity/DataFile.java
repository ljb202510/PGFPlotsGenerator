package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 数据集文件表 data_file */
@Data
@TableName("data_file")
public class DataFile {

    @TableId(value = "data_id", type = IdType.AUTO)
    private Integer dataId;

    private Integer userId;

    private String dataName;

    /** 文件字节数 */
    private Integer dataSize;

    private String description;

    private LocalDateTime loadTime;

    private LocalDateTime updateTime;

    private String fileName;

    private String filePath;

    private String mimetype;
}
