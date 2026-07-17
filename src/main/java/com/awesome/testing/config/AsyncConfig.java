package com.awesome.testing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AsyncConfig {

    @Bean(name = {"taskScheduler", "emailTaskScheduler"})
    public TaskScheduler emailTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("app-scheduler-");
        return scheduler;
    }
}
