package ru.spbstu.hsai.modules.notification.component;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;
import ru.spbstu.hsai.modules.notification.service.NotificationService;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SchedulerComponent {
    private final RepeatingTaskService repeatingTaskService;
    private final NotificationService notificationService;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SchedulerComponent(RepeatingTaskService repeatingTaskService, NotificationService notificationService) {
        this.repeatingTaskService = repeatingTaskService;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void init() {
        System.out.println("Единый планировщик инициализирован и готов");
    }

    public void runUnifiedScheduler() {
        Instant nowUtc = Instant.now();
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(TIME_FORMATTER);
        System.out.println("Проверка задач в " + time);

        repeatingTaskService.getAllTasksToExecute()
                .collectList() // Получаем все задачи один раз
                .flatMapMany(tasks -> {
                    // Сначала уведомляем
                    return notificationService.checkAndNotify(tasks, nowUtc)
                            .thenMany(Flux.fromIterable(tasks)
                                    .flatMap(task -> sendNotification(task.getUserId(), task.getDescription())
                                            .then(repeatingTaskService.processCompletedTask(task, now))));
                })
                .subscribe();
    }


    private Mono<Void> sendNotification(String userId, String message) {
        System.out.println("Уведомление для " + userId + ": " + message);
        return Mono.empty();
    }
}