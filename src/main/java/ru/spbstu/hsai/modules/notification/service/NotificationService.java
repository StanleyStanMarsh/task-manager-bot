package ru.spbstu.hsai.modules.notification.service;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
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
        return aggregateTasksForReminder(nowUtc, "ONE_DAY_BEFORE", Duration.ofDays(1), 0);
    }

    // Проверка задач с напоминанием за неделю (в 00:00)
    public Flux<SimpleTask> findTasksForWeekReminder(Instant nowUtc) {
        return aggregateTasksForReminder(nowUtc, "ONE_WEEK_BEFORE", Duration.ofDays(7), 0);
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
                        findTasksForHourReminder(nowUtc),
                        findTasksForDayReminder(nowUtc),
                        findTasksForWeekReminder(nowUtc)
                )
                .flatMap(this::sendNotification)
                .then();
    }

    private String buildReminderMessage(SimpleTask task, ZoneId userZone) {
        LocalDate deadline = task.getDeadline();
        // 4) Собираем текст
        return  "🔔 Напоминание!"
                + "\n🆔 ID: " + task.getId()
                + "\n📌 Описание: " + task.getDescription()
                + "\n❗️ Дедлайн: " + deadline.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }


    private Mono<Void> sendNotification(SimpleTask task) {
        return userService.findById(task.getUserId())
                .flatMap(user -> {
                    ZoneId userZone = ZoneId.of(user.getTimezone());
                    String message = buildReminderMessage(task, userZone);
                    return notifySender.sendNotification(user.getTelegramId(), message);
                })
                .then();
    }


}
