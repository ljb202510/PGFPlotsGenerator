package com.pg.pgfplots.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统日志表 system_log */
@Data
@TableName("system_log")
public class SystemLog {

    @TableId(value = "sys_id", type = IdType.AUTO)
    private Integer sysId;

    /** normal | warning | error */
    private String systemStatus;

    private LocalDateTime logTime;

    /** 日志内容（截断 500 字） */
    private String error;
}
