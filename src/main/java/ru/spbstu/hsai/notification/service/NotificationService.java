package ru.spbstu.hsai.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.notification.controller.NotificationController;
import ru.spbstu.hsai.repeatingtaskmanagment.RepeatingTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;

import java.time.*;
import java.util.Date;
import java.util.List;

@Service
public class NotificationService {

    private final RepeatingTaskInterface repeatingTaskService;
    private final SimpleTaskInterface taskService;

    public NotificationService(
            RepeatingTaskInterface repeatingTaskService,
            SimpleTaskInterface taskService
    ) {
        this.repeatingTaskService = repeatingTaskService;
        this.taskService = taskService;
    }

    // Проверка задач с напоминанием за час (в 23:00)
    public Flux<SimpleTask> findTasksForHourReminder(Instant nowUtc) {
        return taskService.aggregateTasksForReminder(nowUtc, "ONE_HOUR_BEFORE", Duration.ofHours(1), 23);
    }

    // Проверка задач с напоминанием за день (в 00:00)
    public Flux<SimpleTask> findTasksForDayReminder(Instant nowUtc) {
        return taskService.aggregateTasksForReminder(nowUtc, "ONE_DAY_BEFORE", Duration.ofDays(1), 0);
    }

    // Проверка задач с напоминанием за неделю (в 00:00)
    public Flux<SimpleTask> findTasksForWeekReminder(Instant nowUtc) {
        return taskService.aggregateTasksForReminder(nowUtc, "ONE_WEEK_BEFORE", Duration.ofDays(7), 0);
    }

    public Flux<SimpleTask> findTasksForOverdueReminder(Instant nowUtc) {
        return taskService.aggregateTasksForOverdueReminder(nowUtc, Duration.ofDays(1), 0);
    }

//    private Flux<SimpleTask> aggregateTasksForOverdueReminder(Instant nowUtc, Duration overdueOffset, int targetHour) {
//        return Flux.fromIterable(SUPPORTED_ZONES)
//                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
//                .flatMap(zone -> {
//                    Aggregation aggregation = Aggregation.newAggregation(
//                            createAggregationForOverdueZone(nowUtc, overdueOffset, targetHour, zone)
//                    );
//                    return mongoTemplate.aggregate(aggregation, "simpletasks", SimpleTask.class);
//                });
//    }

//    private AggregationOperation[] createAggregationForOverdueZone(Instant nowUtc, Duration overdueOffset, int targetHour, String zone) {
//        ZoneId zoneId = ZoneId.of(zone);
//        ZonedDateTime nowZoned = nowUtc.atZone(zoneId);
//
//        // Проверяем, соответствует ли текущее время целевому часу (00:00), тут тоже вернутся назад, чуть поправить
//        if (nowZoned.getHour() != targetHour || nowZoned.getMinute() != 0 || nowZoned.getSecond() != 0) {
//            return new AggregationOperation[]{};
//        }
//
//        // Вычисляем дату дедлайна (предыдущий день)
//        ZonedDateTime reminderTime = nowZoned.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
//        ZonedDateTime overdueDate = reminderTime.minus(overdueOffset);
//
//        ZonedDateTime overdueDateMoscow = overdueDate.toLocalDate()
//                .atTime(0, 0)
//                .atZone(ZoneId.of("Europe/Moscow"));
//
//        Instant overdueLikeDB = overdueDateMoscow.toInstant();
//
//        MatchOperation matchOverdueTasks = Aggregation.match(
//                Criteria.where("deadline").is(overdueLikeDB)
//                        .and("isCompleted").is(false)
//        );
//
//        // Этап 2: Подтягивание данных пользователя
//        ProjectionOperation project = Aggregation.project("userId", "description", "deadline", "reminder", "complexity", "isCompleted")
//                .and(ConvertOperators.ToObjectId.toObjectId("$userId"))
//                .as("userIdObjectId");
//
//        // Этап 3: Создаём LookupOperation
//        LookupOperation lookupUser = LookupOperation.newLookup()
//                .from("users")
//                .localField("userIdObjectId")
//                .foreignField("_id")
//                .as("user");
//
//        // Этап 4: Разворачиваем массив user
//        UnwindOperation unwindUser = Aggregation.unwind("user");
//
//        // Этап 5: Фильтрация по часовому поясу пользователя
//        MatchOperation matchTimezone = Aggregation.match(
//                Criteria.where("user.timezone").is(zone)
//        );
//
//        return new AggregationOperation[]{
//                matchOverdueTasks,
//                project,
//                lookupUser,
//                unwindUser,
//                matchTimezone
//        };
//    }

//    private Flux<SimpleTask> aggregateTasksForReminder(Instant nowUtc, String reminderType, Duration reminderOffset, int targetHour) {
//        return Flux.fromIterable(SUPPORTED_ZONES)
//                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
//                .flatMap(zone -> {
//                    Aggregation aggregation = Aggregation.newAggregation(
//                            createAggregationForZone(nowUtc, reminderType, reminderOffset, targetHour, zone)
//                    );
//                    return mongoTemplate.aggregate(aggregation, "simpletasks", SimpleTask.class);
//                });
//    }

    public Mono<Void> checkAndNotify(Instant nowUtc, NotificationController controller) {
        return repeatingTaskService.getAllTasksToExecute()
                .collectList()
                .flatMap(tasks -> {
                    Flux<Void> oneTime = Flux.merge(
                            findTasksForHourReminder(nowUtc).flatMap(t -> controller.sendNotification(t, false)),
                            findTasksForDayReminder(nowUtc).flatMap(t -> controller.sendNotification(t, false)),
                            findTasksForWeekReminder(nowUtc).flatMap(t -> controller.sendNotification(t, false)),
                            findTasksForOverdueReminder(nowUtc).flatMap(t -> controller.sendNotification(t, true))
                    );

                    Flux<Void> repeating = controller.sendRepeatingTaskReminders(tasks, nowUtc);

                    return Flux.merge(oneTime, repeating).then();
                });
    }
}