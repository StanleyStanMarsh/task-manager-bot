package ru.spbstu.hsai.modules.simpletaskmanagment.service;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.simpletaskmanagment.model.SimpleTask;
import ru.spbstu.hsai.modules.simpletaskmanagment.repository.SimpleTaskRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;


@Service
public class SimpleTaskService {
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
    public Flux<SimpleTask> getTodayTasks(String userId) {
        return taskRepository.findTasksByDate(userId, LocalDate.now());
    }


    // Задачи на неделю
    public Flux<SimpleTask> getWeekTasks(String userId) {
        LocalDate start = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
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

}
