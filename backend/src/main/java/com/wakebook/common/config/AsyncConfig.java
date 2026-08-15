package com.wakebook.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 후보군 산출은 외부 API를 수십 번 호출해서 몇 분씩 걸린다. 동시에 여러 도서관을 산출하면
 * 정보나루 호출이 몰리므로 스레드를 좁게 잡고, 큐가 차면 요청을 거절해 작업이 무한정 쌓이지 않게 한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("wakebook-job-");
        executor.initialize();
        return executor;
    }
}
