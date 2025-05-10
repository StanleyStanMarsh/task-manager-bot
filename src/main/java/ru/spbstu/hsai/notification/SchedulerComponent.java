package ru.spbstu.hsai.notification;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.notification.controller.NotificationController;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.notification.service.NotificationService;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SchedulerComponent {
    private final RepeatingTaskInterface repeatingTaskService;
    private final NotificationService notificationService;
    private final NotificationController notificationController;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SchedulerComponent(RepeatingTaskInterface repeatingTaskService, NotificationService notificationService,
                              NotificationController notificationController) {
        this.repeatingTaskService = repeatingTaskService;
        this.notificationService = notificationService;
        this.notificationController = notificationController;
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
                    return notificationService.checkAndNotify(nowUtc, notificationController)
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