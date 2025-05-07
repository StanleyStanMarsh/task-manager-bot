package ru.spbstu.hsai.modules.repeatingtaskmanagment.service;


import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.repository.RepeatingTaskRepository;

import java.time.Instant;
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


    // ищет все задачи, которые не выполнены к тек времени
    //public Flux<RepeatingTask> getAllTasksToExecute(LocalDateTime now) {
      //  return taskRepository.findAllTasksToExecute(now);
   // }

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
}
