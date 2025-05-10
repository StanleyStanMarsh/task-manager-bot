package ru.spbstu.hsai.simpletaskmanagment;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public interface SimpleTaskInterface {
    Mono<SimpleTask> createTask(String userId, String description,
                                int complexity, LocalDate deadline,
                                SimpleTask.ReminderType reminder);

    Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId);

    Flux<SimpleTask> getCompletedTasks(String userId);
    Flux<SimpleTask> getTasksByDate(String userId, LocalDate date);
    Flux<SimpleTask> getActiveTasks(String userId);
    Flux<SimpleTask> getTodayTasks(String userId, ZoneId userZone);
    Flux<SimpleTask> getWeekTasks(String userId, ZoneId userZone);
    Mono<Boolean> taskExistsAndBelongsToUser(String taskId, String userId);
    Flux<SimpleTask> aggregateTasksForReminder(Instant nowUtc, String reminderType, Duration reminderOffset, int targetHour);
    Flux<SimpleTask> aggregateTasksForOverdueReminder(Instant nowUtc, Duration overdueOffset, int targetHour);

    Mono<SimpleTask> findTaskByIdAndUser(String taskId, String userId);

    Mono<SimpleTask> markAsCompleted(String taskId, String userId);

    Mono<SimpleTask> updateTaskDescription(String taskId, String userId, String newDescription);
    Mono<SimpleTask> updateTaskComplexity(String taskId, String userId, Integer newComplexity);
    Mono<SimpleTask> updateTaskDeadline(String taskId, String userId, LocalDate newDeadline);
    Mono<SimpleTask> updateTaskReminder(String taskId, String userId, SimpleTask.ReminderType newReminder);
}
