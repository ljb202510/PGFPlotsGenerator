package com.pg.pgfplots.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 批次1/A1：RAG 异步索引线程池（全工程此前无 @EnableAsync，本次仅为 RAG 索引引入）。
 * <p>队列满直接丢弃并记日志：索引是纯增强，丢了不影响主流程，后续生成会重新 upsert。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("ragIndexExecutor")
    public ThreadPoolTaskExecutor ragIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-index-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((r, e) ->
                org.slf4j.LoggerFactory.getLogger("RagService").warn("[RAG] 索引队列已满，丢弃一条索引任务"));
        executor.initialize();
        return executor;
    }

    /**
     * [G1] 编译任务线程池（批次2/G2 两级限流的第一级）。
     * <p>与 RAG 池相反，编译任务不可静默丢弃：这里保留默认 AbortPolicy，
     * 由 {@code CompileTaskService.submit} 同步捕获拒绝异常并把任务标记 failed 后返回 503。</p>
     * <p>[批次3.6] 队列容量改为可注入（默认 100 不变）。满载阈值 = {@code maxPoolSize(4) + 该值}，
     * 注入小值即可在集成验证中真实触发「队列满 → 503 + 落 COMPILE_QUEUE_FULL」，无需改源码。</p>
     */
    @Bean("compileTaskExecutor")
    public ThreadPoolTaskExecutor compileTaskExecutor(
            @Value("${app.compile.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("compile-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }
}
