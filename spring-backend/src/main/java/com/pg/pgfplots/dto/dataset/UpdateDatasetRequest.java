package com.pg.pgfplots.dto.dataset;

import lombok.Data;

/** 更新数据集请求 */
@Data
public class UpdateDatasetRequest {
    private String name;
    private String description;
}
