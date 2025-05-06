package ru.spbstu.hsai.modules.notification.component;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import ru.spbstu.hsai.modules.notification.service.NotificationService;

import java.time.Duration;
import javax.annotation.PostConstruct;

@Component
public class SchedulerComponent {
    private final NotificationService notificationService;

    public SchedulerComponent(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void scheduleReminderChecks() {
        long delayUntilNextMinute = Duration.ofMillis(60000 - (System.currentTimeMillis() % 60000)).toMillis();

        Flux.interval(Duration.ofMillis(delayUntilNextMinute), Duration.ofMinutes(1))
                .doOnNext(tick -> notificationService.checkAndNotify())
                .subscribe();
    }

}



