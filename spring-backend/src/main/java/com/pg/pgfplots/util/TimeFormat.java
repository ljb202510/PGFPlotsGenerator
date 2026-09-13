package com.pg.pgfplots.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化工具（对齐 Node 前端的 zh-CN 展示习惯）。
 */
public final class TimeFormat {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private TimeFormat() {
    }

    /** yyyy/MM/dd HH:mm */
    public static String toMinute(LocalDateTime time) {
        return time == null ? null : time.format(MINUTE);
    }

    /** yyyy/MM/dd HH:mm:ss */
    public static String toSecond(LocalDateTime time) {
        return time == null ? null : time.format(SECOND);
    }
}
