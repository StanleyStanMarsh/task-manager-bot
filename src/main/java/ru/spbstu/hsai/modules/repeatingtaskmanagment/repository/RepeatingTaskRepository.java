package ru.spbstu.hsai.modules.repeatingtaskmanagment.repository;


import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import org.springframework.data.mongodb.repository.Query;
import reactor.core.publisher.Flux;

import java.time.Instant;

import java.time.LocalDateTime;

@Repository
public interface RepeatingTaskRepository extends ReactiveMongoRepository<RepeatingTask, String> {
    // Для /mytasks (все активные задачи пользователя)
    @Query("{ 'userId': ?0 }")
    Flux<RepeatingTask> findTasksByUserId(String userId);

    @Query("{ 'nextExecution': { $lte: ?0 } }")
    Flux<RepeatingTask> findAllTasksToExecute(LocalDateTime now);

    // Для поиска существующей задачи
    @Query("{ 'userId': ?0, 'description': ?1, 'complexity': ?2, 'frequency': ?3, 'startDateTime': ?4 }")
    Mono<RepeatingTask> findTask(String userId, String description, int complexity,
                                 RepeatingTask.RepeatFrequency frequency, LocalDateTime startDateTime);

    // задачи на день
    @Query("{ 'userId': ?0, 'nextExecution': {$gte: ?1,$lt: ?2 }}")
    Flux<RepeatingTask> findTasksForDay(String userId, LocalDateTime periodStart, LocalDateTime periodEnd);

}
