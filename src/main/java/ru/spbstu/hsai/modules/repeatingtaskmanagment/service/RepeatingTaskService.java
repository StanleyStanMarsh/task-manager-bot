package ru.spbstu.hsai.modules.repeatingtaskmanagment.service;


import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.repository.RepeatingTaskRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class RepeatingTaskService {
    private final RepeatingTaskRepository taskRepository;


    public RepeatingTaskService(RepeatingTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Создание задачи с проверкой на дубликаты
    public Mono<RepeatingTask> createTask(String userId, String description,
                                          int complexity, RepeatingTask.RepeatFrequency frequency,
                                          LocalDateTime startDateTime) {
        return findDuplicateTask(userId, description, complexity, frequency, startDateTime)
                .flatMap(existingTask -> Mono.<RepeatingTask>error(new RuntimeException("EXISTING_TASK:" +
                        existingTask.toString())))
                .switchIfEmpty(Mono.defer(() -> {
                    RepeatingTask task = new RepeatingTask(userId, description, complexity, frequency, startDateTime);
                    return taskRepository.save(task);
                }));
    }


    public Flux<RepeatingTask> getActiveTasks(String userId) {
        return taskRepository.findTasksByUserId(userId);
    }


    // вычисляет след дату выполнения и сохраняет обновленную задачу в бд
    public Mono<RepeatingTask> processCompletedTask(RepeatingTask task, LocalDateTime currentTime) {
        task.calculateNextExecution(currentTime);
        return taskRepository.save(task);
    }

    // Поиск существующей задачи у пользователя
    public Mono<RepeatingTask> findDuplicateTask(String userId, String description,
                                                 int complexity, RepeatingTask.RepeatFrequency frequency,
                                                 LocalDateTime startDateTime) {
        return taskRepository.findTask(userId, description, complexity, frequency, startDateTime);
    }


    public Flux<RepeatingTask> getAllTasksToExecute() {
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findAllTasksToExecute(now);
    }


    // задачи на сегодня
    public Flux<RepeatingTask> getTodayTasks(String userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        return taskRepository.findTasksForDay(userId, startOfDay, endOfDay);
    }

    public Flux<RepeatingTask> getWeekTasks(String userId) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime startOfWeek = monday.atStartOfDay();
        LocalDateTime endOfWeek = sunday.atTime(23, 59, 59);

        return taskRepository.findTasksForWeek(userId, startOfWeek, endOfWeek);

    }

    // на указанную дату
    public Flux<RepeatingTask> getTasksByDate(String userId, LocalDate date) {
        LocalDateTime startDate = date.atStartOfDay();
        LocalDateTime endDate = date.plusDays(1).atStartOfDay();

        return taskRepository.findTasksForDay(userId, startDate, endDate);

    }

    // Удаление задачи
    public Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId) {
        return taskRepository.deleteByIdAndUserId(taskId, userId)
                .map(deletedCount -> deletedCount > 0);
    }

    public Mono<Boolean> taskExistsAndBelongsToUser(String taskId, String userId) {
        return taskRepository.findByIdAndUserId(taskId, userId).hasElement();
    }

    // Обновляем описание задачи
    public Mono<RepeatingTask> updateTaskDescription(String taskId, String userId, String newDescription) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setDescription(newDescription);
                    return taskRepository.save(task);
                });
    }


    // Обновляем сложность
    public Mono<RepeatingTask> updateTaskComplexity(String taskId, String userId, Integer newComplexity) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setComplexity(newComplexity);
                    return taskRepository.save(task);
                });
    }

    // Обновляем периодичность
    public Mono<RepeatingTask> updateTaskFrequency(String taskId, String userId,
                                                    RepeatingTask.RepeatFrequency newFrequency) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setFrequency(newFrequency);
                    return taskRepository.save(task);
                });
    }


    // Обновляем дату и время начала
    public Mono<RepeatingTask> updateTaskStartDateTime(String taskId, String userId,LocalDateTime newStartDateTime) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .flatMap(task -> {
                    task.setStartDateTime(newStartDateTime);
                    return taskRepository.save(task);
                });
    }

}
