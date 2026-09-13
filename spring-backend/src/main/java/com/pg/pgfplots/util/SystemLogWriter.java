package com.pg.pgfplots.util;

import com.pg.pgfplots.entity.SystemLog;
import com.pg.pgfplots.mapper.SystemLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统日志写入工具，对应 Node 的 {@code utils/systemLog.js}。
 * <p>副作用式：自身失败只记录日志，绝不向上抛出，不影响主请求响应。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemLogWriter {

    private static final int MAX_LEN = 500;

    private final SystemLogMapper systemLogMapper;

    /**
     * 写入一条系统日志。
     *
     * @param status  normal | warning | error
     * @param message 日志内容（自动截断 500 字）
     */
    public void write(String status, String message) {
        try {
            SystemLog entry = new SystemLog();
            entry.setSystemStatus(status);
            entry.setLogTime(LocalDateTime.now());
            entry.setError(message == null ? "" : message.substring(0, Math.min(message.length(), MAX_LEN)));
            systemLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("system_log 写入失败: {}", e.getMessage());
        }
    }

    public void info(String message) {
        write("normal", message);
    }

    public void warning(String message) {
        write("warning", message);
    }

    public void error(String message) {
        write("error", message);
    }
}
