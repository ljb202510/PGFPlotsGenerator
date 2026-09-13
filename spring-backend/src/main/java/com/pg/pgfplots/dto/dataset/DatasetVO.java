package com.pg.pgfplots.dto.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 数据集返回视图（字段名保持 snake_case 以兼容前端）。
 */
@Data
public class DatasetVO {

    @JsonProperty("data_id")
    private Integer dataId;

    @JsonProperty("user_id")
    private Integer userId;

    @JsonProperty("data_name")
    private String dataName;

    @JsonProperty("data_size")
    private Integer dataSize;

    private String description;

    @JsonProperty("upload_time")
    private String uploadTime;

    @JsonProperty("update_time")
    private String updateTime;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("file_path")
    private String filePath;

    private String mimetype;

    /** 格式化后的文件大小 */
    private String size;

    /** 兼容前端字段，固定 0 */
    private Integer count;
}
