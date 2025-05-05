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

    // Создание задачи
    public Mono<SimpleTask> createTask(String userId, String description,
                                       int complexity, LocalDate deadline,
                                       SimpleTask.ReminderType reminder) {
        SimpleTask task = new SimpleTask(userId, description, complexity, deadline, reminder);
        return taskRepository.save(task);
    }

    // Получение активных задач пользователя
    public Flux<SimpleTask> getActiveTasks(String userId) {
        return taskRepository.findActiveTasksByUserId(userId);
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
    /*
    public Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId) {
        return taskRepository.findById(taskId)
                .flatMap(task -> {
                    if (task.getUserId().equals(userId)) {
                        return taskRepository.deleteById(taskId)
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }*/

    // Удаление задачи
    public Mono<Boolean> deleteTaskIfBelongsToUser(String taskId, String userId) {
        return taskRepository.deleteByIdAndUserId(taskId, userId)
                .map(deletedCount -> deletedCount > 0);
    }


    // Пометка задачи как выполненной
    public Mono<SimpleTask> markAsCompleted(String taskId) {
        return taskRepository.findById(taskId)
                .flatMap(task -> {
                    task.setCompleted(true);
                    return taskRepository.save(task);
                });
    }

}
