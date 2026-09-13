package com.pg.pgfplots;

import java.util.Arrays;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PGFPlotsGenerator Java 后端启动类。
 * <p>替代原 Node/Express 后端，提供同一套 REST 接口。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.pg.pgfplots.mapper")
public class PgApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PgApplication.class);
        // [RAG] CLI 模式（seed/backfill/demo，见 tools.RagCli）以非 Web 方式运行，执行完退出
        if (Arrays.stream(args).anyMatch(a -> a.startsWith("--rag-cli="))) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
