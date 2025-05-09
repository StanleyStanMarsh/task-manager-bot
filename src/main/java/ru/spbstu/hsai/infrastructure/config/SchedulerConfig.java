package ru.spbstu.hsai.infrastructure.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.spbstu.hsai.modules.notification.component.SchedulerComponent;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {
    private final SchedulerComponent schedulerComponent;

    @Autowired
    public SchedulerConfig(SchedulerComponent schedulerComponent) {
        this.schedulerComponent = schedulerComponent;
    }

    @Bean(destroyMethod = "shutdown")
    public Executor taskScheduler() {
        return Executors.newScheduledThreadPool(5);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());

        // Вычисляем начальную задержку до следующей минуты
        long delayUntilNextMinute = Duration.ofMillis(60000 - (System.currentTimeMillis() % 60000)).toMillis();

        // Планируем executeDueTasks каждые 60 секунд с начальной задержкой
        taskRegistrar.addFixedRateTask(new FixedRateTask(
                schedulerComponent::executeDueTasks,
                60000L, // Интервал
                delayUntilNextMinute // Начальная задержка
        ));

        // Планируем checkAndNotify каждые 60 секунд с начальной задержкой
        taskRegistrar.addFixedRateTask(new FixedRateTask(
                schedulerComponent::checkAndNotify,
                60000L, // Интервал
                delayUntilNextMinute // Начальная задержка
        ));
    }
}