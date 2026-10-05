package com.college.student_service_platform.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/** Bounded workers for long-lived progress requests; no new infrastructure. */
@Configuration
public class AgentStreamingConfig implements WebMvcConfigurer {
    @Bean(name="agentStreamingExecutor")
    public ThreadPoolTaskExecutor agentStreamingExecutor() {
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);executor.setThreadNamePrefix("agent-stream-");executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(agentStreamingExecutor()).setDefaultTimeout(240_000L);
    }
}
