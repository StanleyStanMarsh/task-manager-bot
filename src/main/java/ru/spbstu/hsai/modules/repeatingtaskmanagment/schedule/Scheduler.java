package ru.spbstu.hsai.modules.repeatingtaskmanagment.schedule;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.service.RepeatingTaskService;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class Scheduler {
    RepeatingTaskService repeatingTaskService;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Scheduler(RepeatingTaskService repeatingTaskService) {
        this.repeatingTaskService = repeatingTaskService;
    }

    @PostConstruct
    public void init() {
        System.out.println("Scheduler initialized and ready");
    }

    @Scheduled(fixedRate = 60_000)
    public void executeDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(TIME_FORMATTER);
        System.out.println("Checking tasks at " + time);

        repeatingTaskService.getAllTasksToExecute()
                .flatMap(task -> {
                    // 1. Отправка уведомления
                    return sendNotification(task.getUserId(), task.getDescription())
                            // 2. Обновление даты выполнения
                            .then(repeatingTaskService.processCompletedTask(task, now));
                })
                .subscribe();
    }


    private Mono<Void> sendNotification(String userId, String message) {
        // todo -  заглушка - тут надо реализовать оповещение
        System.out.println("Уведомление для " + userId + ": " + message);
        return Mono.empty();
    }

}
