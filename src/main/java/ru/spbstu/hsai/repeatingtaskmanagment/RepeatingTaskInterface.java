package ru.spbstu.hsai.repeatingtaskmanagment;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public interface RepeatingTaskInterface {
    Flux<RepeatingTask> getAllTasksToExecute();
    Flux<RepeatingTask> getActiveTasks(String userId);
    Flux<RepeatingTask> getTasksByDate(String userId, LocalDate date, ZoneId userZone);
    Flux<RepeatingTask> getTodayTasks(String userId, ZoneId userZone);
    Mono<Boolean> taskExistsAndBelongsToUser(String taskId, String userId);

    Mono<RepeatingTask> createTask(String userId, String description,
                                   int complexity, RepeatingTask.RepeatFrequency frequency,
                                   LocalDateTime startDateTime);

    Mono<RepeatingTask> processCompletedTask(RepeatingTask task, LocalDateTime currentTime);
    Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId);

    Mono<RepeatingTask> updateTaskDescription(String taskId, String userId, String newDescription);
    Mono<RepeatingTask> updateTaskComplexity(String taskId, String userId, Integer newComplexity);
    Mono<RepeatingTask> updateTaskFrequency(String taskId, String userId,
                                            RepeatingTask.RepeatFrequency newFrequency);
    Mono<RepeatingTask> updateTaskStartDateTime(String taskId, String userId,LocalDateTime newStartDateTime);
    Flux<RepeatingTask> getWeekTasks(String userId, ZoneId userZone);
}
