package ru.spbstu.hsai.notification.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.api.TelegramNotifySender;
import ru.spbstu.hsai.notification.service.NotificationService;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTask;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.usermanagement.UserServiceInterface;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationService notificationService;
    private final UserServiceInterface userService;
    private final TelegramNotifySender notifySender;
    private final RepeatingTaskInterface repeatingTaskService;

    @Autowired
    public NotificationController(
            NotificationService notificationService,
            UserServiceInterface userService,
            TelegramNotifySender notifySender,
            RepeatingTaskInterface repeatingTaskService) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.notifySender = notifySender;
        this.repeatingTaskService = repeatingTaskService;
    }

    /**
     * Эндпоинт для ручного запуска уведомлений.
     * @param time Время в формате ISO-8601 (например, 2025-05-10T00:00:00Z).
     * @return Mono<Void> после завершения отправки уведомлений.
     */
    @GetMapping("/trigger")
    public Mono<Void> triggerNotifications(@RequestParam String time) {
        Instant nowUtc;
        try {
            nowUtc = Instant.parse(time);
        } catch (Exception e) {
            logger.error("Неверный формат времени: {}", time, e);
            return Mono.error(new IllegalArgumentException("Неверный формат времени"));
        }
        return notificationService.checkAndNotify(nowUtc, this);
    }

    /**
     * Отправляет уведомление для одной задачи.
     * @param task Задача для отправки уведомления.
     * @param isOverdue Флаг, указывающий, просрочена ли задача.
     * @return Mono<Void> после отправки уведомления.
     */
    public Mono<Void> sendNotification(SimpleTask task, boolean isOverdue) {
        return userService.findById(task.getUserId())
                .flatMap(user -> {
                    ZoneId userZone = ZoneId.of(user.getTimezone());
                    String message = buildReminderMessage(task, userZone, isOverdue);
                    return notifySender.sendNotification(user.getTelegramId(), message);
                })
                .then()
                .onErrorResume(e -> {
                    logger.error("Ошибка при отправке уведомления для задачи {}: {}", task.getId(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Отправляет уведомления для повторяющихся задач.
     * @param repeatingTasks Список повторяющихся задач.
     * @param nowUtc Текущее время в UTC.
     * @return Flux<Void> после отправки всех уведомлений.
     */
    public Flux<Void> sendRepeatingTaskReminders(List<RepeatingTask> repeatingTasks, Instant nowUtc) {
        return Flux.fromIterable(repeatingTasks)
                .flatMap(rt -> userService.findById(rt.getUserId())
                        .flatMap(user -> {
                            ZoneId userZone = ZoneId.of(user.getTimezone());
                            ZonedDateTime nextExecZoned = rt.getNextExecution()
                                    .atZone(ZoneId.of("Europe/Moscow"))
                                    .withZoneSameInstant(userZone);

                            String time = nextExecZoned.toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"));
                            String msg = "🔄 Повторяющаяся задача:" +
                                    "\n🆔 ID: <code>" + rt.getId() + "</code>" +
                                    "\n📌 Описание: " + rt.getDescription() +
                                    "\n⏰ Выполнение: " + time;

                            return notifySender.sendNotification(user.getTelegramId(), msg)
                                    .then(repeatingTaskService.processCompletedTask(
                                            rt,
                                            LocalDateTime.ofInstant(nowUtc, userZone)
                                    ))
                                    .then();
                        })
                        .onErrorResume(e -> {
                            logger.error("Ошибка при обработке повторяющейся задачи {}: {}", rt.getId(), e.getMessage());
                            return Mono.empty();
                        })
                )
                .thenMany(Flux.empty());
    }

    /**
     * Формирует сообщение для напоминания.
     * @param task Задача.
     * @param userZone Часовой пояс пользователя.
     * @param isOverdue Флаг просрочки.
     * @return Текст сообщения.
     */
    private String buildReminderMessage(SimpleTask task, ZoneId userZone, boolean isOverdue) {
        LocalDate deadline = task.getDeadline();
        if (isOverdue) {
            return "⚡️ Дедлайн задачи истек!" +
                    "\n🆔 ID: <code>" + task.getId() + "</code>" +
                    "\n📌 Описание: " + task.getDescription() +
                    "\n Не забудьте отметить задачу завершенной или обновить дедлайн при необходимости.";
        } else {
            return "🔔 Напоминание!" +
                    "\n🆔 ID: <code>" + task.getId() + "</code>" +
                    "\n📌 Описание: " + task.getDescription() +
                    "\n❗️ Дедлайн: " + deadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
    }
}