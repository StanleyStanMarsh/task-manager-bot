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
import java.util.List;

@Service
public class NotificationService {
    private final UserService userService;
    private final TelegramNotifySender notifySender;
    private final SimpleTaskService taskService;
    private static final List<String> SUPPORTED_ZONES = List.of(
            "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara",
            "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Krasnoyarsk",
            "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
            "Asia/Magadan", "Asia/Kamchatka"
    );


    public NotificationService(
                               UserService userService,
                               TelegramNotifySender notifySender, SimpleTaskService taskService) {
        this.userService = userService;
        this.notifySender = notifySender;
        this.taskService =taskService;
    }

    public void checkAndNotify() {
        Instant nowUtc = Instant.now();

        boolean anyZoneMatches = SUPPORTED_ZONES.stream()
                .map(ZoneId::of)
                .map(zone -> ZonedDateTime.ofInstant(nowUtc, zone))
                .anyMatch(time -> (time.getHour() == 0 || time.getHour() == 23) && time.getMinute() == 0);

        if (!anyZoneMatches) {
            return; // Ничего не делаем, если ни в одном нужном поясе не 12:00 или 23:00
        }
        // Сначала отправляем уведомления о напоминаниях
        // TODO Изменить агрегацию тасков:
        //  - можно сделать агрегацию отдельно по ремайндерам: отдельно запрос для выборки по ONE_HOUR_BEFORE, по ONE_DAY_BEFORE, по ONE_WEEK_BEFORE
        //  - при этом в функцию можно передавать часовой пояс, чтобы делать выборку только на один час/день/неделю для конкретного пояса
        //  - условно будет findForOneHourBefore(), findForOneDayBefore(), findForOneWeekBefore(), в которые будет передаваться часовой пояс:
        /*
        public Flux<MyDocument> findForOneDayBefore(ZoneId zoneId) {
            LocalDate zonedYesterday = LocalDate.now(zoneId).minusDays(1);
            Instant start = zonedYesterday.atStartOfDay(zoneId).toInstant();
            Instant end = zonedYesterday.plusDays(1).atStartOfDay(zoneId).toInstant();

            Criteria criteria = new Criteria().andOperator(
                Criteria.where("timestamp").gte(Date.from(start)),
                Criteria.where("timestamp").lt(Date.from(end)),
                Criteria.where("reminder").is("ONE_DAY_BEFORE")
            );

            Query query = new Query(criteria);
            // !!!ReactiveMongoTemplate mongoTemplate
            return mongoTemplate.find(query, MyDocument.class);
        }

        будем проходиться по всем поясам и для каждого пояса вызывать 3 запроса
         */
        Flux<Void> reminderNotifications = taskService.getTasksForTenDays()
                // FIXME агрегацию по фильтру точно вынести в монгу (no_reminder точно можно проверять в монге)
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

                            // FIXME вот это вынести в контроллер:
                            //  - сделать record SimpleTaskNotifyDto(telegramId, taskId, taskDescription, date)
                            //  - вернуть контроллеру Flux<SimpleTaskRemindDto>, а там уже в контроллере распаковать
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
                            // FIXME эту логику также вынести в монгу (см. пример выше)
                            ZoneId zone = ZoneId.of(user.getTimezone());
                            ZonedDateTime now = ZonedDateTime.ofInstant(nowUtc, zone);
                            ZonedDateTime deadline = task.getDeadline().atStartOfDay(zone);

                            // FIXME это тоже вынести в контроллер и сделать аналогично как для напоминаний
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
