package ru.spbstu.hsai.simpletaskmanagment.service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTask;
import ru.spbstu.hsai.simpletaskmanagment.SimpleTaskInterface;
import ru.spbstu.hsai.simpletaskmanagment.repository.SimpleTaskRepository;

import java.time.*;
import java.util.Date;
import java.util.List;


@Service
public class SimpleTaskService implements SimpleTaskInterface {
    private static final List<String> SUPPORTED_ZONES = List.of(
            "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara",
            "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Krasnoyarsk",
            "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
            "Asia/Magadan", "Asia/Kamchatka"
    );

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    private final SimpleTaskRepository taskRepository;

    public SimpleTaskService(SimpleTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Создание задачи с проверкой на дубликаты
    public Mono<SimpleTask> createTask(String userId, String description,
                                       int complexity, LocalDate deadline,
                                       SimpleTask.ReminderType reminder) {
        return findDuplicateTask(userId, description, complexity, deadline, reminder)
                .flatMap(existingTask -> Mono.<SimpleTask>error(new RuntimeException("EXISTING_TASK:" +
                        existingTask.toString())))
                .switchIfEmpty(Mono.defer(() -> {
                    SimpleTask task = new SimpleTask(userId, description, complexity, deadline, reminder);
                    return taskRepository.save(task);
                }));
    }

    // Получение активных задач пользователя
    public Flux<SimpleTask> getActiveTasks(String userId) {
        return taskRepository.findActiveTasksByUserId(userId);
    }

    // Получение завершенных задач пользователя
    public Flux<SimpleTask> getCompletedTasks(String userId) {
        return taskRepository.findCompletedTasksByUserId(userId);
    }

    // Задачи на сегодня
    public Flux<SimpleTask> getTodayTasks(String userId, ZoneId userZone) {
        ZonedDateTime zonedStart = LocalDate.now(userZone).atStartOfDay(userZone);
        // Приводим к московскому времени (если база хранит даты в этом виде)
        LocalDateTime today = zonedStart.toLocalDateTime();
        return taskRepository.findTasksByDate(userId, today.toLocalDate());
    }


    // Задачи на неделю
    public Flux<SimpleTask> getWeekTasks(String userId, ZoneId userZone) {
        // Начало недели — понедельник для указанного часового пояса
        LocalDate start = LocalDate.now(userZone).with(DayOfWeek.MONDAY);
        // Конец недели — воскресенье (плюс 6 дней)
        LocalDate end = start.plusDays(6);
        // Получаем задачи на неделю с учетом часового пояса
        return taskRepository.findTasksForWeek(userId, start, end);
    }

    // Задачи на конкретную дату
    public Flux<SimpleTask> getTasksByDate(String userId, LocalDate date) {
        return taskRepository.findTasksByCustomDate(userId, date);
    }


    // Удаление задачи
    public Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId) {
        return taskRepository.deleteByIdAndUserId(taskId, userId)
                .map(deletedCount -> deletedCount > 0);
    }


    // Поиск существующей задачи у пользователя
    public Mono<SimpleTask> findDuplicateTask(String userId, String description,
                                              int complexity, LocalDate deadline,
                                              SimpleTask.ReminderType reminder) {
        return taskRepository.findTask(userId, description, complexity, deadline, reminder);
    }


    // Поиск задачи по ее id и id пользователя
    public Mono<SimpleTask> findTaskByIdAndUser(String taskId, String userId) {
        return taskRepository.findByIdAndUserId(taskId, userId);
    }

    public Mono<Boolean> taskExistsAndBelongsToUser(String taskId, String userId) {
        return taskRepository.findByIdAndUserId(taskId, userId).hasElement();
    }

    // Обновляем описание задачи
    public Mono<SimpleTask> updateTaskDescription(String taskId, String userId, String newDescription) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setDescription(newDescription);
                    return taskRepository.save(task);
                });
    }

    // Обновляем сложность
    public Mono<SimpleTask> updateTaskComplexity(String taskId, String userId, Integer newComplexity) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setComplexity(newComplexity);
                    return taskRepository.save(task);
                });
    }

    // Обновляем дедлайн
    public Mono<SimpleTask> updateTaskDeadline(String taskId, String userId, LocalDate newDeadline) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setDeadline(newDeadline);
                    return taskRepository.save(task);
                });
    }

    // Обновляем напоминание
    public Mono<SimpleTask> updateTaskReminder(String taskId, String userId, SimpleTask.ReminderType newReminder) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setReminder(newReminder);
                    return taskRepository.save(task);
                });
    }


    // Помечаем задачу как завершенную
    public Mono<SimpleTask> markAsCompleted(String taskId, String userId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setCompleted(true);
                    return taskRepository.save(task);
                });
    }
    // Задачи для просроченных дедлайнов
    public Flux<SimpleTask> getOverdueTasks() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now();
        return taskRepository.findOverdueTasks(start, end);
    }

    // Задачи для напоминаний
    public Flux<SimpleTask> getTasksForTenDays() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(10);
        return taskRepository.findTasksForTenDays(start, end);
    }

    public Flux<SimpleTask> aggregateTasksForReminder(Instant nowUtc, String reminderType, Duration reminderOffset, int targetHour) {
        return Flux.fromIterable(SUPPORTED_ZONES)
                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
                .flatMap(zone -> {
                    Aggregation aggregation = Aggregation.newAggregation(
                            createAggregationForZone(nowUtc, reminderType, reminderOffset, targetHour, zone)
                    );
                    return mongoTemplate.aggregate(aggregation, "simpletasks", SimpleTask.class);
                });
    }

    public Flux<SimpleTask> aggregateTasksForOverdueReminder(Instant nowUtc, Duration overdueOffset, int targetHour) {
        return Flux.fromIterable(SUPPORTED_ZONES)
                .filter(zone -> isTargetTime(nowUtc, targetHour, zone))
                .flatMap(zone -> {
                    Aggregation aggregation = Aggregation.newAggregation(
                            createAggregationForOverdueZone(nowUtc, overdueOffset, targetHour, zone)
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
        ZoneId zoneId = ZoneId.of(zone);
        ZonedDateTime nowZoned = nowUtc.atZone(zoneId);

        // Проверяем, соответствует ли текущее время целевому часу (23:00 или 00:00) (поправить, чтобы лог был нормальным)
        if (nowZoned.getHour() != targetHour || nowZoned.getMinute() != 0 || nowZoned.getSecond() != 0) {
            return new AggregationOperation[]{};
        }

        // Вычисляем дедлайн, который соответствует напоминанию
        ZonedDateTime reminderTime = nowZoned.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime deadlineTime = reminderTime.plus(reminderOffset);

        // Устанавливаем московское время (Europe/Moscow) и время 21:00
        ZonedDateTime deadlineMoscowTime = deadlineTime.toLocalDate()
                .atTime(0, 0)
                .atZone(ZoneId.of("Europe/Moscow"));

        Instant deadlineStart = deadlineMoscowTime.toInstant();
        Instant deadlineEnd = deadlineMoscowTime.plus(Duration.ofMinutes(1)).toInstant();

        MatchOperation matchTasks = Aggregation.match(
                Criteria.where("deadline")
                        .gte(Date.from(deadlineStart))
                        .lt(Date.from(deadlineEnd))
                        .and("reminder").is(reminderType)
        );



        // Шаг 1.5: Подтягивание данных пользователя
        ProjectionOperation project = Aggregation.project("userId", "description", "deadline", "reminder", "complexity", "isCompleted")
                .and(ConvertOperators.ToObjectId.toObjectId("$userId"))
                .as("userIdObjectId");

        // Шаг 2: Создаём LookupOperation
        LookupOperation lookupUser = LookupOperation.newLookup()
                .from("users")
                .localField("userIdObjectId")
                .foreignField("_id")
                .as("user");

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

    private AggregationOperation[] createAggregationForOverdueZone(Instant nowUtc, Duration overdueOffset, int targetHour, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        ZonedDateTime nowZoned = nowUtc.atZone(zoneId);

        // Проверяем, соответствует ли текущее время целевому часу (00:00), тут тоже вернутся назад, чуть поправить
        if (nowZoned.getHour() != targetHour || nowZoned.getMinute() != 0 || nowZoned.getSecond() != 0) {
            return new AggregationOperation[]{};
        }

        // Вычисляем дату дедлайна (предыдущий день)
        ZonedDateTime reminderTime = nowZoned.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime overdueDate = reminderTime.minus(overdueOffset);

        ZonedDateTime overdueDateMoscow = overdueDate.toLocalDate()
                .atTime(0, 0)
                .atZone(ZoneId.of("Europe/Moscow"));

        Instant overdueLikeDB = overdueDateMoscow.toInstant();

        MatchOperation matchOverdueTasks = Aggregation.match(
                Criteria.where("deadline").is(overdueLikeDB)
                        .and("isCompleted").is(false)
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
}
