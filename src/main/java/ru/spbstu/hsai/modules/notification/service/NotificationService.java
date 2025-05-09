package ru.spbstu.hsai.modules.notification.service;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.service.SimpleTaskService;
import ru.spbstu.hsai.modules.usermanagement.service.UserService;
import ru.spbstu.hsai.api.commands.notifier.TelegramNotifySender;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;




@Service
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final UserService userService;
    private final TelegramNotifySender notifySender;
    private final SimpleTaskService taskService;
    private static final List<String> SUPPORTED_ZONES = List.of(
            "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara",
            "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Krasnoyarsk",
            "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
            "Asia/Magadan", "Asia/Kamchatka"
    );

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    public NotificationService(
            UserService userService,
            TelegramNotifySender notifySender,
            SimpleTaskService taskService) {
        this.userService = userService;
        this.notifySender = notifySender;
        this.taskService = taskService;
    }

    // Проверка задач с напоминанием за час (в 23:00)
    public Flux<SimpleTask> findTasksForHourReminder(Instant nowUtc) {
        return aggregateTasksForReminder(nowUtc, "ONE_HOUR_BEFORE", Duration.ofHours(1), 23);
    }

    // Проверка задач с напоминанием за день (в 00:00)
    public Flux<SimpleTask> findTasksForDayReminder(Instant nowUtc) {
        return aggregateTasksForReminder(nowUtc, "ONE_DAY_BEFORE", Duration.ofDays(1), 0);//
    }

    // Проверка задач с напоминанием за неделю (в 00:00)
    public Flux<SimpleTask> findTasksForWeekReminder(Instant nowUtc) {
        return aggregateTasksForReminder(nowUtc, "ONE_WEEK_BEFORE", Duration.ofDays(7), 0);
    }


    public Flux<SimpleTask> findTasksForOverdueReminder(Instant nowUtc) {
        return aggregateTasksForOverdueReminder(nowUtc, Duration.ofDays(1), 0);
    }
    private Flux<SimpleTask> aggregateTasksForOverdueReminder(Instant nowUtc, Duration overdueOffset, int targetHour) {
        return Flux.fromIterable(SUPPORTED_ZONES)
                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
                .flatMap(zone -> {
                    Aggregation aggregation = Aggregation.newAggregation(
                            createAggregationForOverdueZone(nowUtc, overdueOffset, targetHour, zone)
                    );
                    return mongoTemplate.aggregate(aggregation, "simpletasks", SimpleTask.class);
                });
    }

    private AggregationOperation[] createAggregationForOverdueZone(Instant nowUtc, Duration overdueOffset, int targetHour, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        ZonedDateTime nowZoned = nowUtc.atZone(zoneId);

        // Проверяем, соответствует ли текущее время целевому часу (00:00), тут тоже вернутся назад, чуть поправить
        if (nowZoned.getHour() != targetHour || nowZoned.getMinute() != 0 || nowZoned.getSecond() != 0) {
            return new AggregationOperation[]{};
        }

        // Вычисляем дату дедлайна (предыдущий день)
        ZonedDateTime reminderTime = nowZoned.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        Instant overdueDate = reminderTime.minus(overdueOffset).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();; // Дедлайн был вчера


        System.out.println("Overdue date: " + overdueDate);
        System.out.println("Zone: " + zone);

        // Этап 1: Фильтрация задач с просроченным дедлайном
        MatchOperation matchOverdueTasks = Aggregation.match(
                Criteria.where("deadline").is(overdueDate) // Фильтр по точной дате
                        .and("isCompleted").is(false) // Только незавершённые задачи
        );

        // Этап 2: Подтягивание данных пользователя
        ProjectionOperation project = Aggregation.project("userId", "description", "deadline", "reminder", "complexity", "isCompleted")
                .and(ConvertOperators.ToObjectId.toObjectId("$userId"))
                .as("userIdObjectId");

        // Этап 3: Создаём LookupOperation
        LookupOperation lookupUser = LookupOperation.newLookup()
                .from("users")
                .localField("userIdObjectId")
                .foreignField("_id")
                .as("user");

        // Этап 4: Разворачиваем массив user
        UnwindOperation unwindUser = Aggregation.unwind("user");

        // Этап 5: Фильтрация по часовому поясу пользователя
        MatchOperation matchTimezone = Aggregation.match(
                Criteria.where("user.timezone").is(zone)
        );



        return new AggregationOperation[]{
                matchOverdueTasks,
                project,
                lookupUser,
                unwindUser,
                matchTimezone
        };
    }
    private Flux<SimpleTask> aggregateTasksForReminder(Instant nowUtc, String reminderType, Duration reminderOffset, int targetHour) {
        System.out.println("vze");
        return Flux.fromIterable(SUPPORTED_ZONES)
                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
                .flatMap(zone -> {
                    System.out.println("тут");
                    Aggregation aggregation = Aggregation.newAggregation(
                            createAggregationForZone(nowUtc, reminderType, reminderOffset, targetHour, zone)
                    );
                    return mongoTemplate.aggregate(aggregation, "simpletasks", SimpleTask.class);
                });
    }

    private boolean isTargetTime(Instant nowUtc, int targetHour, String zone) {
        ZonedDateTime nowZoned = nowUtc.atZone(ZoneId.of(zone));
        return nowZoned.getHour() == targetHour &&
                nowZoned.getMinute() == 0 &&
                nowZoned.getSecond() == 0;
    }



    private AggregationOperation[] createAggregationForZone(Instant nowUtc, String reminderType, Duration reminderOffset, int targetHour, String zone) {
        System.out.println("тут1");
        ZoneId zoneId = ZoneId.of(zone);
        ZonedDateTime nowZoned = nowUtc.atZone(zoneId);

        // Проверяем, соответствует ли текущее время целевому часу (23:00 или 00:00) (поправить, чтобы лог был нормальным)
        if (nowZoned.getHour() != targetHour || nowZoned.getMinute() != 0 || nowZoned.getSecond() != 0) {
            return new AggregationOperation[]{};
        }

        // Вычисляем дедлайн, который соответствует напоминанию
        ZonedDateTime reminderTime = nowZoned.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime deadlineTime = reminderTime.plus(reminderOffset); //проверяю
        Instant deadlineStart = deadlineTime.toInstant();
        Instant deadlineEnd = deadlineTime.plus(Duration.ofMinutes(1)).toInstant();

        System.out.println(reminderTime);
        System.out.println(deadlineTime);
        System.out.println(deadlineStart);

        // Этап 1: Фильтрация задач по дедлайну и типу напоминания
        MatchOperation matchTasks = Aggregation.match(
                Criteria.where("deadline")
                        .gte(Date.from(deadlineStart))
                        .lt(Date.from(deadlineEnd))
                        .and("reminder").is(reminderType)
        );



        // Шаг 1.5: Подтягивание данных пользователя
        ProjectionOperation project = Aggregation.project("userId", "description", "deadline", "reminder", "complexity", "isCompleted")
                // приводим строку к ObjectId
                .and(ConvertOperators.ToObjectId.toObjectId("$userId"))
                .as("userIdObjectId");

        // Шаг 2: Создаём LookupOperation
        LookupOperation lookupUser = LookupOperation.newLookup()
                .from("users") // коллекция пользователей
                .localField("userIdObjectId") // новое поле с ObjectId
                .foreignField("_id") // поле в users
                .as("user"); // куда положить результат (массив)

        UnwindOperation unwindUser = Aggregation.unwind("user");


        // Этап 3: Фильтрация по часовому поясу пользователя
        MatchOperation matchTimezone = Aggregation.match(
                Criteria.where("user.timezone").is(zone)
        );



        return new AggregationOperation[]{
                matchTasks,
                project,
                lookupUser,
                unwindUser,
                matchTimezone
        };
    }

    public Mono<Void> checkAndNotify() {
        Instant nowUtc = Instant.now();

        return Flux.merge(
                findTasksForHourReminder(nowUtc).flatMap(task -> sendNotification(task, false)),
                findTasksForDayReminder(nowUtc).flatMap(task -> sendNotification(task, false)),
                findTasksForWeekReminder(nowUtc).flatMap(task -> sendNotification(task, false)),
                findTasksForOverdueReminder(nowUtc).flatMap(task -> sendNotification(task, true))
        ).then();
    }


    private String buildReminderMessage(SimpleTask task, ZoneId userZone, boolean isOverdue) {
        LocalDate deadline = task.getDeadline();
        if (isOverdue) {
            return "⚡️ Дедлайн задачи истек!"
                    + "\n🆔 ID: " + task.getId()
                    + "\n📌 Описание: " + task.getDescription()
                    + "\n Не забудьте отметить задачу завершенной или обновить дедлайн при необходимости.";
        } else {
            return "🔔 Напоминание!"
                    + "\n🆔 ID: " + task.getId()
                    + "\n📌 Описание: " + task.getDescription()
                    + "\n❗️ Дедлайн: " + deadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
    }

    private Mono<Void> sendNotification(SimpleTask task, boolean isOverdue) {
        return userService.findById(task.getUserId())
                .flatMap(user -> {
                    ZoneId userZone = ZoneId.of(user.getTimezone());
                    String message = buildReminderMessage(task, userZone, isOverdue);
                    return notifySender.sendNotification(user.getTelegramId(), message);
                })
                .then();
    }

}