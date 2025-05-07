package ru.spbstu.hsai.modules.repeatingtaskmanagment.schedule;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;

import java.time.LocalDateTime;

@Component
public class Scheduler {
    RepeatingTaskService repeatingTaskService;

    public Scheduler(RepeatingTaskService repeatingTaskService) {
        this.repeatingTaskService = repeatingTaskService;
    }

    @Scheduled(fixedRate = 60_000)
    public void executeDueTasks() {
        LocalDateTime now = LocalDateTime.now();

        repeatingTaskService.getAllTasksToExecute(now)
                .flatMap(task -> {
                    // 1. Отправка уведомления (заглушка - реализуйте ваш NotificationService)
                    return sendNotification(task.getUserId(), task.getDescription())
                            // 2. Обновление даты выполнения
                            .then(repeatingTaskService.processCompletedTask(task));
                })
                .subscribe();
    }


    private Mono<Void> sendNotification(String userId, String message) {
        // todo -  заглушка - тут надо реализовать оповещение
        System.out.println("Уведомление для " + userId + ": " + message);
        return Mono.empty();
    }

}
