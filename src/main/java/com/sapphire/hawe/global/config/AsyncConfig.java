package com.sapphire.hawe.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("visionExecutor")
    public ThreadPoolTaskExecutor visionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("vision-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Vision queue full; review discarded"));
        return executor;
    }

    @Bean("insightExecutor")
    public ThreadPoolTaskExecutor insightExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("insight-");
        // A discarded job remains GENERATING and can be restarted after five minutes.
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Insight queue full; generation discarded"));
        return executor;
    }
}
