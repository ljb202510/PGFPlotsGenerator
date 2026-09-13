package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表 users */
@Data
@TableName("users")
public class User {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;

    private String username;

    private String email;

    /** bcrypt 哈希 */
    private String password;

    /** user | admin */
    private String role;

    private LocalDateTime registerTime;
}
