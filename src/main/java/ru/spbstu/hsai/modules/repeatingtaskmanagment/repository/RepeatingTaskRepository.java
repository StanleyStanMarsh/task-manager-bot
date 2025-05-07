package ru.spbstu.hsai.modules.repeatingtaskmanagment.repository;


import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.modules.repeatingtaskmanagment.model.RepeatingTask;
import org.springframework.data.mongodb.repository.Query;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;

@Repository
public interface RepeatingTaskRepository extends ReactiveMongoRepository<RepeatingTask, String> {
    // Для /mytasks (все активные задачи пользователя)
    @Query("{ 'userId': ?0 }")
    Flux<RepeatingTask> findTasksByUserId(String userId);

    @Query("{ 'userId': ?0, 'nextExecution': { $lte: ?1 }, 'isCompleted': false }")
    Flux<RepeatingTask> findTasksToExecute(String userId, LocalDateTime now);

    @Query("{ 'nextExecution': { $lte: ?0 }, 'isCompleted': false }")
    Flux<RepeatingTask> findAllTasksToExecute(LocalDateTime now);

    //@Query("{ 'userId': ?0, 'nextExecution': { $gte: ?1, $lte: ?2 }, 'isCompleted': false }")
    //Flux<RepeatingTask> findTasksForPeriod(String userId, LocalDateTime start, LocalDateTime end);

    // Для поиска существующей задачи
    @Query("{ 'userId': ?0, 'description': ?1, 'complexity': ?2, 'frequency': ?3, 'startDateTime': ?4 }")
    Mono<RepeatingTask> findTask(String userId, String description, int complexity,
                                 RepeatingTask.RepeatFrequency frequency, LocalDateTime startDateTime);


}
