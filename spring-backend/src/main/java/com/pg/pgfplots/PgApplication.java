package com.pg.pgfplots;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
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
        SpringApplication.run(PgApplication.class, args);
    }
}
