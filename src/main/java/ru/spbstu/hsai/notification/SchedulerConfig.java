package ru.spbstu.hsai.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class SchedulerConfig {
    private final SchedulerComponent schedulerComponent;

    @Autowired
    public SchedulerConfig(SchedulerComponent schedulerComponent) {
        this.schedulerComponent = schedulerComponent;
    }

    @Bean(destroyMethod = "shutdown")
    public Executor taskScheduler() {
        return Executors.newScheduledThreadPool(5);
    }

    // Метод будет запускаться каждую минуту в 00 секунд
    @Scheduled(cron = "0 * * * * *")
    public void runSchedulerAtStartOfMinute() {
        schedulerComponent.runUnifiedScheduler();
    }
}
