package ru.spbstu.hsai.modules.notification.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.repository.SimpleTaskRepository;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import ru.spbstu.hsai.api.commands.notifier.TelegramNotifySender;
import ru.spbstu.hsai.modules.usermanagement.model.User;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationService {
    private final UserService userService;
    private final TelegramNotifySender notifySender;
    private final SimpleTaskService taskService;



    public NotificationService(
                               UserService userService,
                               TelegramNotifySender notifySender, SimpleTaskService taskService) {
        this.userService = userService;
        this.notifySender = notifySender;
        this.taskService =taskService;
    }

    public void checkAndNotify() {
        Instant nowUtc = Instant.now();

        // Сначала отправляем уведомления о напоминаниях
        Flux<Void> reminderNotifications = taskService.getTasksForTenDays()
                .filter(task ->  task.getReminder() != SimpleTask.ReminderType.NO_REMINDER)
                .flatMap(task -> userService.findById(task.getUserId())
                        .flatMap(user -> {
                            if (user.getTimezone() == null) {
                                return notifySender.sendNotification(user.getTelegramId(),
                                        "Пожалуйста, укажите часовой пояс с помощью /settimezone");
                            }

                            ZoneId zone = ZoneId.of(user.getTimezone());
                            ZonedDateTime now = ZonedDateTime.ofInstant(nowUtc, zone);
                            // Дедлайн в 00:00 в часовом поясе пользователя
                            ZonedDateTime deadline = task.getDeadline().atStartOfDay(zone);

                            ZonedDateTime remindTime = switch (task.getReminder()) {
                                case ONE_HOUR_BEFORE -> deadline.minusHours(1); // 23:00 предыдущего дня
                                //case FIVE_MINUTES_BEFORE -> deadline.plusMinutes(229); // убрать
                                case ONE_DAY_BEFORE -> deadline.minusDays(1);
                                case ONE_WEEK_BEFORE -> deadline.minusWeeks(1);
                                default -> null;
                            };

                            if (remindTime != null && isSameMinute(now, remindTime)) {
                                String message = "🔔 Напоминание!"
                                        + "\n🆔 ID: " + task.getId()
                                        + "\n📌 Описание: " + task.getDescription()
                                        + "\n❗ Дедлайн: " + deadline.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                                return notifySender.sendNotification(user.getTelegramId(), message);
                            }
                            return Mono.empty();
                        }));

        // Затем отправляем уведомления о просроченных задачах
        Flux<Void> overdueNotifications = taskService.getOverdueTasks()
                .flatMap(task -> userService.findById(task.getUserId())
                        .flatMap(user -> {
                            if (user.getTimezone() == null) {
                                return Mono.empty(); // Уже уведомили о часовом поясе выше
                            }

                            ZoneId zone = ZoneId.of(user.getTimezone());
                            ZonedDateTime now = ZonedDateTime.ofInstant(nowUtc, zone);
                            ZonedDateTime deadline = task.getDeadline().atStartOfDay(zone);

                            if (isSameMinute(now/*.minusMinutes(229)*/, deadline.plusDays(1))) { //deadline.plusDays(1) оставить, now оставить
                                String message = "⚡ Дедлайн задачи истек! "
                                        + "\n🆔 ID: " +  task.getId()
                                        + "\n📌 Описание: " + task.getDescription()
                                        + "\n Не забудьте отметить задачу завершенной или обновить дедлайн при необходимости.";
                                return notifySender.sendNotification(user.getTelegramId(), message);
                            }
                            return Mono.empty();
                        }));

        // Объединяем потоки: сначала напоминания, затем просрочки
        reminderNotifications
                .thenMany(overdueNotifications)
                .subscribe();
    }

    private boolean isSameMinute(ZonedDateTime a, ZonedDateTime b) {

        return a.getYear() == b.getYear()
                && a.getMonth() == b.getMonth()
                && a.getDayOfMonth() == b.getDayOfMonth()
                && a.getHour() == b.getHour()
                && a.getMinute() == b.getMinute();
    }
}
