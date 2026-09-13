package com.pg.pgfplots.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.File;

/**
 * Web 相关初始化：启动时确保文件目录存在（对应 Node 的启动期 mkdir）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final AppProperties appProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initDirectories() {
        AppProperties.Storage storage = appProperties.getStorage();
        ensureDir(storage.getUploads());
        ensureDir(storage.getHistory());
        ensureDir(storage.getCharts());
        ensureDir(storage.getDebug());

        // 启动即打印实际解析出的绝对路径，便于确认与 Node 版读写的是同一批目录（避免联调冲突）
        log.info("工作目录: {}", new File("").getAbsolutePath());
        log.info("uploads 目录 : {}", new File(storage.getUploads()).getAbsolutePath());
        log.info("history 目录 : {}", new File(storage.getHistory()).getAbsolutePath());
        log.info("charts  目录 : {}", new File(storage.getCharts()).getAbsolutePath());
        log.info("debug   目录 : {}", new File(storage.getDebug()).getAbsolutePath());
        log.info("generation_path 解析基准: {}", new File(storage.getRelativeBase()).getAbsolutePath());
    }

    private void ensureDir(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("目录创建失败: {}", dir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("目录创建异常: {} - {}", path, e.getMessage());
        }
    }
}
