package com.pg.pgfplots.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 管理员用户列表视图。 */
@Data
public class AdminUserVO {

    @JsonProperty("user_id")
    private Integer userId;

    private String username;

    private String email;

    private String role;

    @JsonProperty("register_time")
    private String registerTime;
}
